package com.vswitch.datainjection.device.presence;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceHistoryServiceTest {

    @Mock private PresenceHistoryStore store;

    private PresenceHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PresenceHistoryService(store, 365);
    }

    @Test
    void recordOnlinePersistsTransition() {
        Instant at = Instant.parse("2026-06-12T10:00:00Z");

        service.recordOnline("tenant-1", "wm001", at, PresenceTransitionRecord.SOURCE_HEARTBEAT);

        ArgumentCaptor<PresenceTransitionRecord> captor =
                ArgumentCaptor.forClass(PresenceTransitionRecord.class);
        verify(store).putEvent(captor.capture());
        PresenceTransitionRecord record = captor.getValue();
        assertEquals("WM001", record.deviceId());
        assertEquals(PresenceTransitionRecord.STATUS_ONLINE, record.status());
        assertEquals(PresenceTransitionRecord.SOURCE_HEARTBEAT, record.source());
        assertEquals("tenant-1", record.tenantId());
    }

    @Test
    void recordOfflinePersistsTransition() {
        Instant at = Instant.parse("2026-06-12T10:05:00Z");

        service.recordOffline("tenant-1", "WM001", at, PresenceTransitionRecord.SOURCE_TIMEOUT);

        ArgumentCaptor<PresenceTransitionRecord> captor =
                ArgumentCaptor.forClass(PresenceTransitionRecord.class);
        verify(store).putEvent(captor.capture());
        assertEquals(PresenceTransitionRecord.STATUS_OFFLINE, captor.getValue().status());
        assertEquals(PresenceTransitionRecord.SOURCE_TIMEOUT, captor.getValue().source());
    }
}
