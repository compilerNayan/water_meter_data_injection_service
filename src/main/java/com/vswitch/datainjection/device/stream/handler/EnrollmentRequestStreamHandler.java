package com.vswitch.datainjection.device.stream.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.DevicePreEnrollService;
import com.vswitch.datainjection.DeviceTenantLookupResponse;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamHttpRequestBuilder;
import com.vswitch.datainjection.device.stream.command.DeviceStreamSession;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

/**
 * Handles device {@code enrollment_request} uplink: resolves tenant from pre-enrollment data,
 * then calls device {@code EnrollmentNotificationController} over the same TCP session via
 * {@code POST /deviceenrollment/notify} or {@code POST /deviceenrollment/failure}.
 */
@Component
public class EnrollmentRequestStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentRequestStreamHandler.class);

    static final String NOTIFY_PATH = "/deviceenrollment/notify";
    static final String FAILURE_PATH = "/deviceenrollment/failure";

    private final DevicePreEnrollService preEnrollService;
    private final DeviceStreamCommandService commandService;
    private final ObjectMapper objectMapper;

    EnrollmentRequestStreamHandler(
            DevicePreEnrollService preEnrollService,
            DeviceStreamCommandService commandService,
            ObjectMapper objectMapper) {
        this.preEnrollService = preEnrollService;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    public void handle(DeviceStreamEnvelope envelope, DeviceStreamSession session) {
        // Run on the TCP reader thread so the downlink is sent before the device can drop.
        processEnrollment(envelope, session);
    }

    void processEnrollment(DeviceStreamEnvelope envelope, DeviceStreamSession session) {
        String serialNumber = resolveSerialNumber(envelope);
        if (serialNumber == null) {
            log.warn("Ignoring enrollment_request without serialNumber");
            return;
        }
        if (session == null) {
            log.warn("Ignoring enrollment_request for serial={} without TCP session", serialNumber);
            return;
        }

        log.info("Processing enrollment_request for serial={}", serialNumber);

        DeviceTenantLookupResponse lookup;
        try {
            lookup = preEnrollService.lookupTenantBySerial(serialNumber);
        } catch (ResponseStatusException ex) {
            log.warn(
                    "Tenant lookup failed for serial={}: {}",
                    serialNumber,
                    ex.getReason() != null ? ex.getReason() : ex.getMessage());
            notifyEnrollmentFailure(session, serialNumber, ex);
            return;
        }

        notifyEnrollmentSuccess(session, envelope, lookup, serialNumber);
    }

    private void notifyEnrollmentSuccess(
            DeviceStreamSession session,
            DeviceStreamEnvelope envelope,
            DeviceTenantLookupResponse lookup,
            String serialNumber) {
        try {
            String notifyBody = buildNotifyBody(envelope, lookup, serialNumber);
            String httpRequest = DeviceStreamHttpRequestBuilder.buildPost(NOTIFY_PATH, notifyBody);
            commandService.sendHttpDownlinkOnSession(session, httpRequest);
            log.info("Enrollment notify downlink sent for serial={}", serialNumber);
        } catch (Exception ex) {
            log.error(
                    "Failed to notify device of enrollment success for serial={}",
                    serialNumber,
                    ex);
        }
    }

    private void notifyEnrollmentFailure(
            DeviceStreamSession session, String serialNumber, ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        String code =
                ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                        ? "TENANT_NOT_FOUND"
                        : "ENROLLMENT_FAILED";
        notifyEnrollmentFailure(session, serialNumber, reason, code);
    }

    private void notifyEnrollmentFailure(
            DeviceStreamSession session, String serialNumber, String reason, String code) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("serialNumber", serialNumber);
            body.put("reason", reason);
            body.put("code", code);
            String json = objectMapper.writeValueAsString(body);
            String httpRequest = DeviceStreamHttpRequestBuilder.buildPost(FAILURE_PATH, json);
            commandService.sendHttpDownlinkOnSession(session, httpRequest);
            log.info("Enrollment failure downlink sent for serial={}", serialNumber);
        } catch (Exception ex) {
            log.warn(
                    "Failed to notify device of enrollment failure for serial={}",
                    serialNumber,
                    ex);
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
