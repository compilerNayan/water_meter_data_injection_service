package com.vswitch.datainjection.live;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.UserService;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLiveWebSocketHandlerTest {

    @Mock private TenantLiveSessionRegistry sessionRegistry;
    @Mock private UserService userService;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private WebSocketSession session;

    private TenantLiveWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler =
                new TenantLiveWebSocketHandler(
                        sessionRegistry, userService, jwtDecoder, objectMapper);
        when(session.getAttributes()).thenReturn(new java.util.HashMap<>());
    }

    @Test
    void subscribeRegistersSessionForValidTenantMember() throws Exception {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("user-123")
                        .build();
        when(jwtDecoder.decode("good-token")).thenReturn(jwt);

        String payload =
                objectMapper.writeValueAsString(
                        Map.of("type", "subscribe", "tenantId", "tenant-1", "token", "good-token"));
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(userService).requireTenantMember("user-123", "tenant-1");
        verify(sessionRegistry).register("tenant-1", session);
        assertTrue(session.getAttributes().containsKey(TenantLiveWebSocketHandler.ATTR_SUBSCRIBED));
    }

    @Test
    void subscribeRejectsUnauthorizedTenant() throws Exception {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("user-123")
                        .build();
        when(jwtDecoder.decode("good-token")).thenReturn(jwt);
        doThrow(
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Tenant does not match authenticated user"))
                .when(userService)
                .requireTenantMember("user-123", "tenant-2");

        String payload =
                objectMapper.writeValueAsString(
                        Map.of("type", "subscribe", "tenantId", "tenant-2", "token", "good-token"));
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(sessionRegistry).unregister(session);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void subscribeRequiresSubscribeMessageType() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("type", "ping"));
        handler.handleTextMessage(session, new TextMessage(payload));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
}
