package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.device.DeviceFacade;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DevicePresenceServiceTest {

    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    @Mock private DeviceFacade deviceFacade;

    private DevicePresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new DevicePresenceService(liveUpdateBroadcaster, deviceFacade);
    }

    @Test
    void firstPulseMarksDeviceOnlineAndBroadcasts() {
        Instant ts = Instant.now();

        presenceService.recordPulse("tenant-1", "WM001", ts);

        assertTrue(presenceService.isOnline("WM001"));
        verify(deviceFacade).touchHeartbeat("tenant-1", "WM001", ts);

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals("device_presence", message.type());
        assertEquals("online", message.status());
        assertEquals("WM001", message.deviceId());
    }

    @Test
    void repeatedPulseDoesNotRebroadcastOnline() {
        Instant ts = Instant.now();
        presenceService.recordPulse("tenant-1", "WM001", ts);
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-1"), org.mockito.ArgumentMatchers.any());

        presenceService.recordPulse("tenant-1", "WM001", ts.plusSeconds(1));

        verifyNoMoreInteractions(liveUpdateBroadcaster);
        assertTrue(presenceService.isOnline("WM001"));
    }

    @Test
    void expireStaleDevicesMarksOfflineAndBroadcasts() {
        Instant ts = Instant.now().minusSeconds(31);
        presenceService.recordPulse("tenant-1", "WM001", ts);

        presenceService.expireStaleDevices();

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
}
