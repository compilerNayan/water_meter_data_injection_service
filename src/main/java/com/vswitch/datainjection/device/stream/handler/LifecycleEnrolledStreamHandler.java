package com.vswitch.datainjection.device.stream.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.vswitch.datainjection.EnrollmentCompletionService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

/**
 * Handles device {@code lifecycle_enrolled} uplink over TCP: marks unit and pre-enroll records
 * complete, same as MQTT {@code lifecycle/enrolled} handled by {@link
 * com.vswitch.datainjection.device.IotMqttIngestionService}.
 */
@Component
public class LifecycleEnrolledStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleEnrolledStreamHandler.class);

    private final EnrollmentCompletionService enrollmentCompletionService;

    LifecycleEnrolledStreamHandler(EnrollmentCompletionService enrollmentCompletionService) {
        this.enrollmentCompletionService = enrollmentCompletionService;
    }

    public void handle(DeviceStreamEnvelope envelope) {
        String tenantId = resolveTenantId(envelope);
        String deviceId = resolveDeviceId(envelope);
        String serialNumber = resolveSerialNumber(envelope, deviceId);
        String enrolledAt = readDataString(envelope.data(), "enrolledAt", null);

        if (tenantId == null || tenantId.isBlank()) {
            log.warn(
                    "Ignoring lifecycle_enrolled without tenantId from serial={}",
                    envelope.serialNumber());
            return;
        }
        if (deviceId == null || deviceId.isBlank()) {
            log.warn(
                    "Ignoring lifecycle_enrolled without deviceId from serial={}",
                    envelope.serialNumber());
            return;
        }

        if (!deviceId.equals(serialNumber)) {
            log.warn(
                    "lifecycle_enrolled deviceId {} differs from serialNumber {}",
                    deviceId,
                    serialNumber);
        }

        log.info(
                "Processing lifecycle_enrolled for device={} tenant={}",
                deviceId,
                tenantId);

        try {
            enrollmentCompletionService.onEnrolled(tenantId, deviceId, enrolledAt);
        } catch (ResponseStatusException ex) {
            log.warn(
                    "Failed to complete enrollment for device={} tenant={}: {}",
                    deviceId,
                    tenantId,
                    ex.getReason() != null ? ex.getReason() : ex.getMessage());
        } catch (Exception ex) {
            log.error(
                    "Unexpected error completing enrollment for device={} tenant={}",
                    deviceId,
                    tenantId,
                    ex);
        }
    }

    private static String resolveTenantId(DeviceStreamEnvelope envelope) {
        String fromData = readDataString(envelope.data(), "tenantId", null);
        if (fromData != null && !fromData.isBlank()) {
            return fromData.trim();
        }
        if (envelope.tenantId() != null && !envelope.tenantId().isBlank()) {
            return envelope.tenantId().trim();
        }
        return null;
    }

    private static String resolveDeviceId(DeviceStreamEnvelope envelope) {
        String fromData = readDataString(envelope.data(), "deviceId", null);
        if (fromData != null && !fromData.isBlank()) {
            return fromData.trim();
        }
        return resolveSerialNumber(envelope, null);
    }

    private static String resolveSerialNumber(DeviceStreamEnvelope envelope, String deviceId) {
        String fromData = readDataString(envelope.data(), "serialNumber", null);
        if (fromData != null && !fromData.isBlank()) {
            return fromData.trim();
        }
        if (envelope.serialNumber() != null && !envelope.serialNumber().isBlank()) {
            return envelope.serialNumber().trim();
        }
        return deviceId;
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
