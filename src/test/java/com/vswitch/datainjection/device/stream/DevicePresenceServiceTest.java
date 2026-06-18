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

    private DevicePresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new DevicePresenceService(liveUpdateBroadcaster, connectionRegistry);
    }

    @Test
    void markOnlineBroadcastsOnce() {
        Instant ts = Instant.now();

        presenceService.markOnline("tenant-1", "WM001", ts);

        assertTrue(presenceService.isOnline("WM001"));

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals("device_presence", message.type());
        assertEquals("online", message.status());
        assertEquals("WM001", message.deviceId());
    }

    @Test
    void repeatedMarkOnlineDoesNotRebroadcast() {
        Instant ts = Instant.now();
        presenceService.markOnline("tenant-1", "WM001", ts);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());

        presenceService.markOnline("tenant-1", "WM001", ts.plusSeconds(1));

        verifyNoMoreInteractions(liveUpdateBroadcaster);
        assertTrue(presenceService.isOnline("WM001"));
    }

    @Test
    void markOfflineBroadcastsOnceAndStaysOffline() {
        Instant ts = Instant.now();
        presenceService.markOnline("tenant-1", "WM001", ts);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());

        presenceService.markOffline("tenant-1", "WM001", ts.plusSeconds(1));

        assertFalse(presenceService.isOnline("WM001"));
        assertFalse(
                presenceService.resolveIsOnline(
                        "WM001",
                        Optional.of(
                                new DeviceStateRecord(
                                        "WM001",
                                        "tenant-1",
                                        0,
                                        0,
                                        DeviceStateRecord.STATUS_FLOWING,
                                        100,
                                        100,
                                        100,
                                        ts.toString(),
                                        "",
                                        ts.toString()))));

        presenceService.markOffline("tenant-1", "WM001", ts.plusSeconds(2));
        org.mockito.Mockito.verify(liveUpdateBroadcaster, org.mockito.Mockito.times(2))
                .broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());
        verifyNoMoreInteractions(liveUpdateBroadcaster);
    }

    @Test
    void touchLastSeenDoesNotMarkOnline() {
        Instant ts = Instant.now();

        presenceService.touchLastSeen("tenant-1", "WM001", ts);

        assertFalse(presenceService.isOnline("WM001"));
        verifyNoMoreInteractions(liveUpdateBroadcaster);
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
