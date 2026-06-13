package com.vswitch.datainjection.live;

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
import com.vswitch.datainjection.UserService;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Component
@ConditionalOnProperty(name = "live.updates.enabled", havingValue = "true", matchIfMissing = true)
public class TenantLiveWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantLiveWebSocketHandler.class);
    static final String ATTR_TENANT_ID = "tenantId";
    static final String ATTR_SUBSCRIBED = "subscribed";

    private final TenantLiveSessionRegistry sessionRegistry;
    private final UserService userService;
    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    TenantLiveWebSocketHandler(
            TenantLiveSessionRegistry sessionRegistry,
            UserService userService,
            JwtDecoder jwtDecoder,
            ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.userService = userService;
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (Boolean.TRUE.equals(session.getAttributes().get(ATTR_SUBSCRIBED))) {
            return;
        }

        Map<String, Object> payload =
                objectMapper.readValue(message.getPayload(), new TypeReference<>() {});

        String type = stringValue(payload.get("type"));
        if (!"subscribe".equals(type)) {
            sendErrorAndClose(session, "invalid_message", "First message must be subscribe");
            return;
        }

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

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
