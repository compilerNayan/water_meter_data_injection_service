package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceStreamIngestionServiceTest {

    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;

    private DeviceLiveTelemetryStore store;
    private DeviceStreamIngestionService service;

    @BeforeEach
    void setUp() {
        store = new DeviceLiveTelemetryStore();
        service = new DeviceStreamIngestionService(store, liveUpdateBroadcaster);
    }

    @Test
    void storesPulseAndBroadcastsWhenMlPositive() {
        Instant ts = Instant.parse("2026-06-13T10:00:05Z");
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1", "QJPDXN094", null, ts, 45, 123.456));

        DeviceLiveTelemetrySnapshot snapshot =
                store.find("QJPDXN094").orElseThrow();
        assertEquals("63tk0y1", snapshot.tenantId());
        assertEquals("QJPDXN094", snapshot.deviceId());
        assertEquals(45, snapshot.ml());
        assertEquals(123.456, snapshot.cumulativeLiters(), 0.001);
        assertEquals(2.7, snapshot.flowRateLpm(), 0.001);

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("63tk0y1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals("water_flow", message.type());
        assertEquals("QJPDXN094", message.deviceId());
        assertEquals(45, message.ml());
        assertEquals(123.456, message.cumulativeLiters());
    }

    @Test
    void storesPulseWithoutBroadcastWhenMlZero() {
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1",
                        "QJPDXN094",
                        null,
                        Instant.parse("2026-06-13T10:00:05Z"),
                        0,
                        120.0));

        assertTrue(store.find("QJPDXN094").isPresent());
        verify(liveUpdateBroadcaster, never())
                .broadcast(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void clearLiveTelemetryRemovesSnapshot() {
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1",
                        "QJPDXN094",
                        null,
                        Instant.now(),
                        10,
                        100));

        service.clearLiveTelemetry("QJPDXN094");

        assertTrue(store.find("QJPDXN094").isEmpty());
    }
}
