package com.vswitch.datainjection.live;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.device.logs.DeviceLogEntry;

@Component
public class DeviceLogBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DeviceLogBroadcaster.class);

    public static final String ATTR_LOG_WATCH_DEVICE_ID = "logWatchDeviceId";

    private final TenantLiveSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public DeviceLogBroadcaster(
            TenantLiveSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${live.updates.enabled:true}") boolean enabled) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void broadcastEntries(String tenantId, List<DeviceLogEntry> entries) {
        if (!enabled || entries == null || entries.isEmpty()) {
            return;
        }
        for (DeviceLogEntry entry : entries) {
            broadcastToWatchers(tenantId, entry.deviceId(), LiveUpdateMessage.deviceLog(entry));
        }
    }

    public void broadcastReset(String tenantId, String deviceId) {
        if (!enabled) {
            return;
        }
        broadcastToWatchers(
                tenantId, deviceId, LiveUpdateMessage.deviceLogReset(tenantId, deviceId));
    }

    public void sendCatchUp(
            WebSocketSession session, String tenantId, String deviceId, List<DeviceLogEntry> entries) {
        if (!enabled || entries == null || entries.isEmpty()) {
            return;
        }
        try {
            sendMessage(session, LiveUpdateMessage.deviceLogBatch(tenantId, deviceId, entries));
        } catch (Exception e) {
            log.warn(
                    "Failed to send device_log_batch to session {} for {}/{}",
                    session.getId(),
                    tenantId,
                    deviceId,
                    e);
        }
    }

    private void broadcastToWatchers(String tenantId, String deviceId, LiveUpdateMessage message) {
        if (tenantId == null || tenantId.isBlank() || deviceId == null || deviceId.isBlank()) {
            return;
        }
        String normalizedDevice = deviceId.trim().toUpperCase();
        for (WebSocketSession session : sessionRegistry.subscribers(tenantId)) {
            if (!session.isOpen()) {
                continue;
            }
            Object watched = session.getAttributes().get(ATTR_LOG_WATCH_DEVICE_ID);
            if (watched == null || !normalizedDevice.equals(watched.toString().trim().toUpperCase())) {
                continue;
            }
            try {
                sendMessage(session, message);
            } catch (Exception e) {
                log.warn(
                        "Failed to send device log to session {} for tenant {}",
                        session.getId(),
                        tenantId,
                        e);
            }
        }
    }

    private void sendMessage(WebSocketSession session, LiveUpdateMessage message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }
}
