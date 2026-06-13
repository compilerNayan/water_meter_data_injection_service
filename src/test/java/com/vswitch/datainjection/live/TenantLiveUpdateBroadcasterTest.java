package com.vswitch.datainjection.live;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLiveUpdateBroadcasterTest {

    @Mock private TenantLiveSessionRegistry sessionRegistry;
    @Mock private WebSocketSession session;

    private TenantLiveUpdateBroadcaster broadcaster;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        broadcaster = new TenantLiveUpdateBroadcaster(sessionRegistry, objectMapper, true);
    }

    @Test
    void broadcastDoesNothingWhenNoSubscribers() throws Exception {
        when(sessionRegistry.subscribers("tenant-1")).thenReturn(Set.of());

        broadcaster.broadcast(
                "tenant-1",
                LiveUpdateMessage.waterFlow(
                        "tenant-1",
                        "WM000001",
                        "wm-WM000001",
                        Instant.parse("2026-06-09T10:30:05Z"),
                        45,
                        2.7,
                        123.45));

        verify(session, never()).sendMessage(any());
    }

    @Test
    void broadcastSendsJsonToOpenSessions() throws Exception {
        when(sessionRegistry.subscribers("tenant-1")).thenReturn(Set.of(session));
        when(session.isOpen()).thenReturn(true);

        broadcaster.broadcast(
                "tenant-1",
                LiveUpdateMessage.bucket30m(
                        "tenant-1",
                        "WM000001",
                        "wm-WM000001",
                        Instant.parse("2026-06-09T10:00:00Z")));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        var json = objectMapper.readTree(captor.getValue().getPayload());
        assertEquals("bucket_30m", json.get("type").asText());
        assertEquals("refresh", json.get("action").asText());
        assertEquals("WM000001", json.get("deviceId").asText());
    }

    @Test
    void broadcastSkipsWhenDisabled() throws Exception {
        broadcaster = new TenantLiveUpdateBroadcaster(sessionRegistry, objectMapper, false);

        broadcaster.broadcast(
                "tenant-1",
                LiveUpdateMessage.subscribed("tenant-1"));

        verify(sessionRegistry, never()).subscribers(any());
    }

    @Test
    void broadcastSkipsBlankTenantId() throws Exception {
        broadcaster.broadcast(" ", LiveUpdateMessage.subscribed("tenant-1"));
        verify(sessionRegistry, never()).subscribers(any());
    }
}
