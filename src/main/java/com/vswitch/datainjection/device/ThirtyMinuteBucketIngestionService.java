package com.vswitch.datainjection.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vswitch.datainjection.device.stream.DeviceStreamIngestionService;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

/**
 * Ingests 30-minute water bucket payloads from any transport (TCP stream, MQTT legacy).
 */
@Service
public class ThirtyMinuteBucketIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ThirtyMinuteBucketIngestionService.class);

    private final DeviceFacade deviceFacade;
    private final DeviceStreamIngestionService deviceStreamIngestionService;
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;

    ThirtyMinuteBucketIngestionService(
            DeviceFacade deviceFacade,
            DeviceStreamIngestionService deviceStreamIngestionService,
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster) {
        this.deviceFacade = deviceFacade;
        this.deviceStreamIngestionService = deviceStreamIngestionService;
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
    }

    public void ingestStreamEnvelope(
            String envelopeTenantId, String envelopeSerialNumber, JsonNode data) {
        if (data == null || data.isNull()) {
            throw new IllegalArgumentException("water_30m envelope missing data");
        }

        String tenantId = firstNonBlank(readText(data, "tenantId"), envelopeTenantId);
        String deviceId =
                firstNonBlank(
                        readText(data, "deviceId"),
                        firstNonBlank(readText(data, "serialNumber"), envelopeSerialNumber));
        String periodStartRaw = readText(data, "periodStart");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        if (periodStartRaw == null || periodStartRaw.isBlank()) {
            throw new IllegalArgumentException("periodStart is required");
        }

        Instant periodStart = Instant.parse(periodStartRaw);
        List<MinuteBucketEntry> minutes = parseMinutes(data.get("minutes"));
        double cumulativeLiters = readDouble(data, "cumulativeLiters", 0.0);
        double valveTargetPercent = readDouble(data, "valveTargetPercent", 100.0);

        ingest(
                new ThirtyMinuteBucketPayload(
                        tenantId,
                        deviceId,
                        periodStart,
                        minutes,
                        cumulativeLiters,
                        valveTargetPercent));
    }

    public void ingestMap(Map<String, Object> payload) {
        String tenantId = stringField(payload, "tenantId");
        String deviceId = firstNonBlank(stringField(payload, "deviceId"), stringField(payload, "serialNumber"));
        String periodStartRaw = stringField(payload, "periodStart");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        if (periodStartRaw == null || periodStartRaw.isBlank()) {
            throw new IllegalArgumentException("periodStart is required");
        }

        Instant periodStart = Instant.parse(periodStartRaw);
        List<MinuteBucketEntry> minutes = parseMinuteMaps(payload.get("minutes"));
        double cumulativeLiters = doubleField(payload, "cumulativeLiters", 0.0);
        double valveTargetPercent = doubleField(payload, "valveTargetPercent", 100.0);

        ingest(
                new ThirtyMinuteBucketPayload(
                        tenantId,
                        deviceId,
                        periodStart,
                        minutes,
                        cumulativeLiters,
                        valveTargetPercent));
    }

    public void ingest(ThirtyMinuteBucketPayload payload) {
        deviceFacade.ingest30MinuteBucket(payload);
        deviceStreamIngestionService.clearLiveTelemetry(payload.deviceId());
        broadcastBucket30m(payload.tenantId(), payload.deviceId(), payload.periodStart());
        log.info(
                "Ingested 30-minute bucket for device={} tenant={} periodStart={}",
                payload.deviceId(),
                payload.tenantId(),
                payload.periodStart());
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

    private static List<MinuteBucketEntry> parseMinutes(JsonNode minutesNode) {
        List<MinuteBucketEntry> minutes = new ArrayList<>();
        if (minutesNode == null || !minutesNode.isArray()) {
            return minutes;
        }
        for (JsonNode minuteNode : minutesNode) {
            String t = readText(minuteNode, "t");
            if (t == null || t.isBlank()) {
                continue;
            }
            minutes.add(new MinuteBucketEntry(Instant.parse(t), readDouble(minuteNode, "ml", 0.0)));
        }
        return minutes;
    }

    @SuppressWarnings("unchecked")
    private static List<MinuteBucketEntry> parseMinuteMaps(Object minutesObject) {
        List<MinuteBucketEntry> minutes = new ArrayList<>();
        if (!(minutesObject instanceof List<?> minuteMaps)) {
            return minutes;
        }
        for (Object entry : minuteMaps) {
            if (!(entry instanceof Map<?, ?> minute)) {
                continue;
            }
            String t = stringField((Map<String, Object>) minute, "t");
            if (t == null || t.isBlank()) {
                continue;
            }
            minutes.add(
                    new MinuteBucketEntry(Instant.parse(t), doubleField((Map<String, Object>) minute, "ml", 0.0)));
        }
        return minutes;
    }

    private static String readText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static double readDouble(JsonNode node, String field, double defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        return Double.parseDouble(value.asText());
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

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }
}
