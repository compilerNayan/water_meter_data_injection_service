package com.vswitch.datainjection.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TenantLiveUpdateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TenantLiveUpdateBroadcaster.class);

    private final TenantLiveSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public TenantLiveUpdateBroadcaster(
            TenantLiveSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${live.updates.enabled:true}") boolean enabled) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void broadcast(String tenantId, LiveUpdateMessage message) {
        if (!enabled) {
            return;
        }
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }

        var subscribers = sessionRegistry.subscribers(tenantId);
        if (subscribers.isEmpty()) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(message);
            TextMessage frame = new TextMessage(payload);
            for (WebSocketSession session : subscribers) {
                if (!session.isOpen()) {
                    continue;
                }
                try {
                    synchronized (session) {
                        session.sendMessage(frame);
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to send live update to session {} for tenant {}",
                            session.getId(),
                            tenantId,
                            e);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to serialize live update for tenant {}", tenantId, e);
        }
    }
}
