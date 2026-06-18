package com.vswitch.datainjection.live;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.UnitService;
import com.vswitch.datainjection.UserService;
import com.vswitch.datainjection.device.logs.DeviceLogEntry;
import com.vswitch.datainjection.device.logs.DeviceLogStore;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Component
@ConditionalOnProperty(name = "live.updates.enabled", havingValue = "true", matchIfMissing = true)
public class TenantLiveWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantLiveWebSocketHandler.class);
    static final String ATTR_TENANT_ID = "tenantId";
    static final String ATTR_SUBSCRIBED = "subscribed";
    static final String ATTR_LOG_WATCH_LAST_SEQ = "logWatchLastSeq";

    private final TenantLiveSessionRegistry sessionRegistry;
    private final UserService userService;
    private final UnitService unitService;
    private final DeviceLogStore logStore;
    private final DeviceLogBroadcaster logBroadcaster;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    TenantLiveWebSocketHandler(
            TenantLiveSessionRegistry sessionRegistry,
            UserService userService,
            UnitService unitService,
            DeviceLogStore logStore,
            DeviceLogBroadcaster logBroadcaster,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.userService = userService;
        this.unitService = unitService;
        this.logStore = logStore;
        this.logBroadcaster = logBroadcaster;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload =
                objectMapper.readValue(message.getPayload(), new TypeReference<>() {});

        String type = stringValue(payload.get("type"));
        if (Boolean.TRUE.equals(session.getAttributes().get(ATTR_SUBSCRIBED))) {
            handlePostSubscribeMessage(session, type, payload);
            return;
        }

        if (!"subscribe".equals(type)) {
            sendErrorAndClose(session, "invalid_message", "First message must be subscribe");
            return;
        }

        handleSubscribe(session, payload);
    }

    private void handlePostSubscribeMessage(
            WebSocketSession session, String type, Map<String, Object> payload) throws Exception {
        if ("log_watch".equals(type)) {
            handleLogWatch(session, payload);
            return;
        }
        if ("log_unwatch".equals(type)) {
            session.getAttributes().remove(DeviceLogBroadcaster.ATTR_LOG_WATCH_DEVICE_ID);
            session.getAttributes().remove(ATTR_LOG_WATCH_LAST_SEQ);
            return;
        }
        sendMessage(session, LiveUpdateMessage.error("invalid_message", "Unknown message type"));
    }

    private void handleLogWatch(WebSocketSession session, Map<String, Object> payload)
            throws Exception {
        String tenantId = stringValue(session.getAttributes().get(ATTR_TENANT_ID));
        if (tenantId == null) {
            sendMessage(session, LiveUpdateMessage.error("forbidden", "Not subscribed"));
            return;
        }

        String deviceId = stringValue(payload.get("deviceId"));
        if (deviceId == null) {
            sendMessage(session, LiveUpdateMessage.error("invalid_device", "deviceId is required"));
            return;
        }
        String normalizedDevice = deviceId.trim().toUpperCase();

        if (unitService.findByTenantAndDeviceId(tenantId, normalizedDevice).isEmpty()) {
            sendMessage(session, LiveUpdateMessage.error("not_found", "Device not found"));
            return;
        }

        long lastSeq = parseLastSeq(payload.get("lastSeq"));
        session.getAttributes().put(DeviceLogBroadcaster.ATTR_LOG_WATCH_DEVICE_ID, normalizedDevice);
        session.getAttributes().put(ATTR_LOG_WATCH_LAST_SEQ, lastSeq);

        List<DeviceLogEntry> catchUp =
                logStore.readAfterSeq(tenantId, normalizedDevice, lastSeq);
        logBroadcaster.sendCatchUp(session, tenantId, normalizedDevice, catchUp);
        if (!catchUp.isEmpty()) {
            session.getAttributes()
                    .put(ATTR_LOG_WATCH_LAST_SEQ, catchUp.get(catchUp.size() - 1).seq());
        }
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload)
            throws Exception {
        String tenantId = stringValue(payload.get("tenantId"));
        String token = stringValue(payload.get("token"));
        if (tenantId == null || tenantId.isBlank()) {
            sendErrorAndClose(session, "invalid_tenant", "tenantId is required");
            return;
        }
        if (token == null || token.isBlank()) {
            sendErrorAndClose(session, "invalid_token", "token is required");
            return;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.debug("WebSocket subscribe rejected: invalid JWT", e);
            sendErrorAndClose(session, "invalid_token", "Invalid or expired token");
            return;
        }

        try {
            userService.requireTenantMember(jwt.getSubject(), tenantId);
        } catch (Exception e) {
            log.debug(
                    "WebSocket subscribe rejected for user {} tenant {}",
                    jwt.getSubject(),
                    tenantId,
                    e);
            sendErrorAndClose(session, "forbidden", "Not authorized for tenant");
            return;
        }

        session.getAttributes().put(ATTR_TENANT_ID, tenantId);
        session.getAttributes().put(ATTR_SUBSCRIBED, true);
        sessionRegistry.register(tenantId, session);
        sendMessage(session, LiveUpdateMessage.subscribed(tenantId));
    }

    private void sendErrorAndClose(WebSocketSession session, String code, String errorMessage) {
        try {
            sendMessage(session, LiveUpdateMessage.error(code, errorMessage));
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            log.warn("Failed to close WebSocket session after auth error", e);
        } finally {
            sessionRegistry.unregister(session);
        }
    }

    private void sendMessage(WebSocketSession session, LiveUpdateMessage message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private static long parseLastSeq(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
