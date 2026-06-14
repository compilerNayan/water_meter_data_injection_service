package com.vswitch.datainjection.device.stream.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.DevicePreEnrollService;
import com.vswitch.datainjection.DeviceTenantLookupResponse;
import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamHttpRequestBuilder;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

import jakarta.annotation.PreDestroy;

@Component
public class EnrollmentRequestStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentRequestStreamHandler.class);

    private final DevicePreEnrollService preEnrollService;
    private final DeviceStreamCommandService commandService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor =
            Executors.newCachedThreadPool(r -> new Thread(r, "enrollment-stream-handler"));

    EnrollmentRequestStreamHandler(
            DevicePreEnrollService preEnrollService,
            DeviceStreamCommandService commandService,
            ObjectMapper objectMapper) {
        this.preEnrollService = preEnrollService;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    public void handle(DeviceStreamEnvelope envelope) {
        executor.execute(() -> process(envelope));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void process(DeviceStreamEnvelope envelope) {
        String serialNumber = resolveSerialNumber(envelope);
        if (serialNumber == null) {
            log.warn("Ignoring enrollment_request without serialNumber");
            return;
        }

        log.info("Processing enrollment_request for serial={}", serialNumber);

        try {
            DeviceTenantLookupResponse lookup = preEnrollService.lookupTenantBySerial(serialNumber);
            String notifyBody = buildNotifyBody(envelope, lookup, serialNumber);
            String httpRequest =
                    DeviceStreamHttpRequestBuilder.buildPost("/deviceenrollment/notify", notifyBody);
            DeviceMqttHttpResponse response =
                    commandService.sendHttpCommandAndAwaitResponse(serialNumber, httpRequest);

            log.info(
                    "Enrollment notify response for serial={}: status={} body={}",
                    serialNumber,
                    response.statusCode(),
                    response.body());
        } catch (ResponseStatusException ex) {
            log.warn(
                    "Enrollment failed for serial={}: {}",
                    serialNumber,
                    ex.getReason() != null ? ex.getReason() : ex.getMessage());
            notifyFailure(serialNumber, ex);
        } catch (Exception ex) {
            log.error("Unexpected enrollment error for serial={}", serialNumber, ex);
            notifyFailure(serialNumber, ex.getMessage(), "ENROLLMENT_ERROR");
        }
    }

    private void notifyFailure(String serialNumber, ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        String code = ex.getStatusCode().value() == 404 ? "TENANT_NOT_FOUND" : "ENROLLMENT_FAILED";
        notifyFailure(serialNumber, reason, code);
    }

    private void notifyFailure(String serialNumber, String reason, String code) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("serialNumber", serialNumber);
            body.put("reason", reason);
            body.put("code", code);
            String json = objectMapper.writeValueAsString(body);
            String httpRequest =
                    DeviceStreamHttpRequestBuilder.buildPost("/deviceenrollment/failure", json);
            DeviceMqttHttpResponse response =
                    commandService.sendHttpCommandAndAwaitResponse(serialNumber, httpRequest);
            log.info(
                    "Enrollment failure notify response for serial={}: status={} body={}",
                    serialNumber,
                    response.statusCode(),
                    response.body());
        } catch (Exception ex) {
            log.warn("Failed to notify device of enrollment failure for serial={}", serialNumber, ex);
        }
    }

    private String buildNotifyBody(
            DeviceStreamEnvelope envelope,
            DeviceTenantLookupResponse lookup,
            String serialNumber)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", lookup.tenantId());
        body.put("serialNumber", serialNumber);
        body.put("deviceType", readDataString(envelope.data(), "deviceType", "water_meter"));
        String thingName = readDataString(envelope.data(), "thingName", serialNumber);
        if (thingName != null) {
            body.put("thingName", thingName);
        }
        return objectMapper.writeValueAsString(body);
    }

    private static String resolveSerialNumber(DeviceStreamEnvelope envelope) {
        if (envelope.serialNumber() != null && !envelope.serialNumber().isBlank()) {
            return envelope.serialNumber().trim();
        }
        return readDataString(envelope.data(), "serialNumber", null);
    }

    private static String readDataString(JsonNode data, String field, String defaultValue) {
        if (data == null || data.isNull()) {
            return defaultValue;
        }
        JsonNode node = data.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? defaultValue : text;
    }
}
