package com.vswitch.datainjection.device.stream;

import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.device.presence.PresenceHistoryService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamConnectionRegistry;
import com.vswitch.datainjection.device.stream.command.DeviceStreamSession;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePresenceServiceTest {

    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    @Mock private DeviceStreamConnectionRegistry connectionRegistry;
    @Mock private PresenceHistoryService presenceHistoryService;

    private DevicePresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService =
                new DevicePresenceService(
                        liveUpdateBroadcaster, connectionRegistry, presenceHistoryService);
    }

    @Test
    void firstHeartbeatMarksDeviceOnlineAndBroadcasts() {
        Instant ts = Instant.now();

        presenceService.recordHeartbeat("tenant-1", "WM001", ts);

        assertTrue(presenceService.isOnline("WM001"));

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals("device_presence", message.type());
        assertEquals("online", message.status());
        assertEquals("WM001", message.deviceId());
    }

    @Test
    void repeatedHeartbeatDoesNotRebroadcastOnline() {
        Instant ts = Instant.now();
        presenceService.recordHeartbeat("tenant-1", "WM001", ts);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());

        presenceService.recordHeartbeat("tenant-1", "WM001", ts.plusSeconds(1));

        verifyNoMoreInteractions(liveUpdateBroadcaster);
        assertTrue(presenceService.isOnline("WM001"));
    }

    @Test
    void markOfflineBroadcastsOnceAndStaysOfflineDespiteRecentHeartbeat() {
        Instant ts = Instant.now();
        presenceService.recordHeartbeat("tenant-1", "WM001", ts);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());

        presenceService.markOffline("tenant-1", "WM001", ts.plusSeconds(1));

        assertFalse(presenceService.isOnline("WM001"));

        presenceService.recordHeartbeat("tenant-1", "WM001", ts.plusSeconds(2));
        assertTrue(presenceService.isOnline("WM001"));
    }

    @Test
    void expireStaleHeartbeatsMarksOfflineAndBroadcasts() {
        Instant ts = Instant.now().minusSeconds(6);
        presenceService.recordHeartbeat("tenant-1", "WM001", ts);

        presenceService.expireStaleHeartbeats();

        assertFalse(presenceService.isOnline("WM001"));

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster, org.mockito.Mockito.atLeastOnce())
                .broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage offlineMessage =
                captor.getAllValues().stream()
                        .filter(message -> "offline".equals(message.status()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("device_presence", offlineMessage.type());
    }

    @Test
    void resolveIsOnlineUsesActiveSocketWhenPresenceCacheEmpty() {
        when(connectionRegistry.findBySerial("WM001"))
                .thenReturn(Optional.of(new DeviceStreamSession(new StringWriter())));

        assertTrue(presenceService.resolveIsOnline("WM001", Optional.empty()));
    }

    @Test
    void resolveIsOnlineUsesPersistedOfflineStatusWhenNoSocket() {
        assertFalse(
                presenceService.resolveIsOnline(
                        "WM001",
                        Optional.of(
                                new DeviceStateRecord(
                                        "WM001",
                                        "tenant-1",
                                        0,
                                        0,
                                        DeviceStateRecord.STATUS_OFFLINE,
                                        100,
                                        100,
                                        100,
                                        Instant.now().toString(),
                                        "",
                                        Instant.now().toString()))));
    }
}
