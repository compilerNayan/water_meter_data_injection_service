package com.vswitch.datainjection.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.EnrollmentCompletionService;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

@Service
public class IotMqttIngestionService {

    private static final Logger log = LoggerFactory.getLogger(IotMqttIngestionService.class);

    private final DeviceFacade deviceFacade;
    private final EnrollmentCompletionService enrollmentCompletionService;
    private final DeviceMqttResponseTracker responseTracker;
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final ObjectMapper objectMapper;

    IotMqttIngestionService(
            DeviceFacade deviceFacade,
            EnrollmentCompletionService enrollmentCompletionService,
            DeviceMqttResponseTracker responseTracker,
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster,
            ObjectMapper objectMapper) {
        this.deviceFacade = deviceFacade;
        this.enrollmentCompletionService = enrollmentCompletionService;
        this.responseTracker = responseTracker;
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
        this.objectMapper = objectMapper;
    }

    public void handleMqttMessage(String topic, byte[] payloadBytes) {
        String body = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> event = new HashMap<>();
        event.put("mqttTopic", topic);
        try {
            Map<String, Object> json =
                    objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            event.putAll(json);
        } catch (Exception ignored) {
            event.put("payload", body);
        }
        try {
            handleEvent(event);
        } catch (Exception e) {
            log.error("Failed to ingest MQTT message on topic {}", topic, e);
        }
    }

    public void handleEvent(Map<String, Object> event) {
        Map<String, Object> normalized = normalizeEvent(event);
        String topic = stringField(normalized, "mqttTopic", "topic");
        if (topic == null || topic.isBlank()) {
            log.warn("MQTT event missing topic field");
            return;
        }

        MqttTopicParser.ParsedMqttTopic parsed =
                MqttTopicParser.parse(topic).orElse(null);
        if (parsed == null) {
            log.debug("Ignoring unrecognized MQTT topic {}", topic);
            return;
        }

        switch (parsed.suffix()) {
            case DeviceMqttTopics.SUFFIX_LIFECYCLE_ENROLLED -> handleEnrolled(parsed, normalized);
            case DeviceMqttTopics.SUFFIX_WATER_1S -> handleSecondPulse(parsed, normalized);
            case DeviceMqttTopics.SUFFIX_WATER_30M -> handleThirtyMinuteBucket(parsed, normalized);
            case DeviceMqttTopics.SUFFIX_STATUS -> handleStatusResponse(parsed, normalized);
            default -> log.debug("Ignoring MQTT topic suffix {}", parsed.suffix());
        }
    }

    private Map<String, Object> normalizeEvent(Map<String, Object> event) {
        if (event == null) {
            return Map.of();
        }
        String payloadBase64 = stringField(event, "payloadBase64");
        if (payloadBase64 == null) {
            return event;
        }

        Map<String, Object> merged = new HashMap<>(event);
        String decoded = DeviceMqttHttpPayloadParser.decodeBase64(payloadBase64);
        try {
            Map<String, Object> json =
                    objectMapper.readValue(decoded, new TypeReference<Map<String, Object>>() {});
            merged.putAll(json);
            return merged;
        } catch (Exception ignored) {
            merged.put("payload", decoded);
            return merged;
        }
    }

    private void handleEnrolled(MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        String tenantId = firstNonBlank(stringField(event, "tenantId"), parsed.tenantId());
        String deviceId = firstNonBlank(stringField(event, "deviceId"), parsed.deviceId());
        String serialNumber = firstNonBlank(stringField(event, "serialNumber"), deviceId);
        if (!deviceId.equals(serialNumber)) {
            log.warn(
                    "Enrollment payload deviceId {} differs from serialNumber {}",
                    deviceId,
                    serialNumber);
        }
        String enrolledAt = stringField(event, "enrolledAt");
        enrollmentCompletionService.onEnrolled(tenantId, deviceId, enrolledAt);
    }

    private void handleSecondPulse(MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        String tenantId = parsed.tenantId();
        String deviceId = parsed.deviceId();
        String tsRaw = stringField(event, "ts");
        Instant ts =
                tsRaw != null && !tsRaw.isBlank() ? Instant.parse(tsRaw) : Instant.now();
        double ml = doubleField(event, "ml");
        deviceFacade.ingestSecondPulse(tenantId, deviceId, ts, ml);
        if (ml > 0) {
            broadcastWaterFlow(tenantId, deviceId, ts, ml);
        }
    }

    private void handleThirtyMinuteBucket(
            MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            Map<String, Object> payload =
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            String tenantId =
                    firstNonBlank(stringField(payload, "tenantId"), parsed.tenantId());
            String deviceId =
                    firstNonBlank(stringField(payload, "deviceId"), parsed.deviceId());
            String periodStartRaw = stringField(payload, "periodStart");
            Instant periodStart = Instant.parse(periodStartRaw);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> minuteMaps =
                    (List<Map<String, Object>>) payload.get("minutes");
            List<MinuteBucketEntry> minutes = new ArrayList<>();
            if (minuteMaps != null) {
                for (Map<String, Object> minute : minuteMaps) {
                    String t = stringField(minute, "t");
                    double ml = doubleField(minute, "ml");
                    minutes.add(new MinuteBucketEntry(Instant.parse(t), ml));
                }
            }

            double cumulativeLiters = doubleField(payload, "cumulativeLiters");
            double valveTargetPercent = doubleField(payload, "valveTargetPercent", 100.0);

            deviceFacade.ingest30MinuteBucket(
                    new ThirtyMinuteBucketPayload(
                            tenantId,
                            deviceId,
                            periodStart,
                            minutes,
                            cumulativeLiters,
                            valveTargetPercent));
            broadcastBucket30m(tenantId, deviceId, periodStart);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 30-minute bucket payload", e);
        }
    }

    private void broadcastWaterFlow(
            String tenantId, String deviceId, Instant ts, double ml) {
        try {
            double flowRateLpm = ml / 1000.0 * 60;
            liveUpdateBroadcaster.broadcast(
                    tenantId,
                    LiveUpdateMessage.waterFlow(
                            tenantId, deviceId, unitIdFor(deviceId), ts, ml, flowRateLpm));
        } catch (Exception e) {
            log.warn("Failed to broadcast water_flow for {}/{}", tenantId, deviceId, e);
        }
    }

    private void broadcastBucket30m(String tenantId, String deviceId, Instant periodStart) {
        try {
            liveUpdateBroadcaster.broadcast(
                    tenantId,
                    LiveUpdateMessage.bucket30m(
                            tenantId, deviceId, unitIdFor(deviceId), periodStart));
        } catch (Exception e) {
            log.warn("Failed to broadcast bucket_30m for {}/{}", tenantId, deviceId, e);
        }
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }

    private void handleStatusResponse(
            MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        DeviceMqttHttpResponse httpResponse =
                DeviceMqttHttpResponseParser.parse(event, objectMapper);
        Map<String, Object> responseBody = httpResponse.body();

        responseTracker.completeResponse(parsed.tenantId(), parsed.deviceId(), httpResponse);

        if (hasValveFields(responseBody)) {
            double target =
                    firstPresentDouble(
                            responseBody,
                            "targetPressurePercent",
                            "target",
                            "valveTargetPercent");
            double actual =
                    firstPresentDouble(
                            responseBody,
                            "actualPressurePercent",
                            "actual",
                            "valveActualPercent");
            deviceFacade.ingestValveStateReport(
                    parsed.tenantId(), parsed.deviceId(), target, actual);
        }
    }

    private static boolean hasValveFields(Map<String, Object> payload) {
        return firstPresentKey(
                        payload,
                        "targetPressurePercent",
                        "target",
                        "valveTargetPercent",
                        "actualPressurePercent",
                        "actual",
                        "valveActualPercent")
                != null;
    }

    private static String stringField(Map<String, Object> event, String... keys) {
        for (String key : keys) {
            Object value = event.get(key);
            if (value != null) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static double doubleField(Map<String, Object> event, String key) {
        return doubleField(event, key, 0.0);
    }

    private static double doubleField(Map<String, Object> event, String key, double defaultValue) {
        Object value = event.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static double firstPresentDouble(Map<String, Object> event, String... keys) {
        String key = firstPresentKey(event, keys);
        if (key == null) {
            return 0.0;
        }
        return doubleField(event, key);
    }

    private static String firstPresentKey(Map<String, Object> event, String... keys) {
        for (String key : keys) {
            if (event.containsKey(key) && event.get(key) != null) {
                return key;
            }
        }
        return null;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
