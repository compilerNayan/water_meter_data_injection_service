package com.vswitch.datainjection.device.stream.handler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.vswitch.datainjection.device.presence.PresenceHistoryService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

/**
 * Handles device {@code device_boot} uplink: records a boot marker in presence history without
 * changing live online/offline state.
 */
@Component
public class DeviceBootStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceBootStreamHandler.class);

    private final PresenceHistoryService presenceHistoryService;

    DeviceBootStreamHandler(PresenceHistoryService presenceHistoryService) {
        this.presenceHistoryService = presenceHistoryService;
    }

    public void handle(DeviceStreamEnvelope envelope) {
        String tenantId = resolveTenantId(envelope);
        String deviceId = resolveDeviceId(envelope);

        if (tenantId == null || tenantId.isBlank()) {
            log.warn(
                    "Ignoring device_boot without tenantId from serial={}",
                    envelope.serialNumber());
            return;
        }
        if (deviceId == null || deviceId.isBlank()) {
            log.warn(
                    "Ignoring device_boot without deviceId from serial={}",
                    envelope.serialNumber());
            return;
        }

        log.info("Recording device_boot for device={} tenant={}", deviceId, tenantId);
        presenceHistoryService.recordBoot(tenantId, deviceId, Instant.now());
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
        if (envelope.serialNumber() != null && !envelope.serialNumber().isBlank()) {
            return envelope.serialNumber().trim();
        }
        return null;
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
