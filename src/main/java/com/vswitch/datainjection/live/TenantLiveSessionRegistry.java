package com.vswitch.datainjection.live;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class TenantLiveSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByTenant =
            new ConcurrentHashMap<>();

    public void register(String tenantId, WebSocketSession session) {
        sessionsByTenant
                .computeIfAbsent(tenantId, ignored -> new CopyOnWriteArraySet<>())
                .add(session);
    }

    public void unregister(WebSocketSession session) {
        sessionsByTenant.values().forEach(set -> set.remove(session));
    }

    public Set<WebSocketSession> subscribers(String tenantId) {
        Set<WebSocketSession> sessions = sessionsByTenant.get(tenantId);
        if (sessions == null || sessions.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(sessions);
    }
}
