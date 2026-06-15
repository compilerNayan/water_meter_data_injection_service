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
    @Mock private com.vswitch.datainjection.device.DeviceFacade deviceFacade;

    private DeviceLiveTelemetryStore store;
    private DevicePresenceService presenceService;
    private DeviceStreamIngestionService service;

    @BeforeEach
    void setUp() {
        store = new DeviceLiveTelemetryStore();
        presenceService = new DevicePresenceService(liveUpdateBroadcaster, deviceFacade);
        service = new DeviceStreamIngestionService(store, liveUpdateBroadcaster, presenceService);
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
        verify(liveUpdateBroadcaster, org.mockito.Mockito.atLeastOnce())
                .broadcast(eq("63tk0y1"), captor.capture());
        LiveUpdateMessage message =
                captor.getAllValues().stream()
                        .filter(item -> LiveUpdateMessage.TYPE_WATER_FLOW.equals(item.type()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("QJPDXN094", message.deviceId());
        assertEquals(45, message.ml());
        assertEquals(123.456, message.cumulativeLiters());
    }

    @Test
    void storesPulseWithoutBroadcastingWaterFlowWhenMlZero() {
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1",
                        "QJPDXN094",
                        null,
                        Instant.parse("2026-06-13T10:00:05Z"),
                        0,
                        120.0));

        assertTrue(store.find("QJPDXN094").isPresent());
        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("63tk0y1"), captor.capture());
        assertEquals(LiveUpdateMessage.TYPE_DEVICE_PRESENCE, captor.getValue().type());
        verify(liveUpdateBroadcaster, org.mockito.Mockito.never())
                .broadcast(
                        eq("63tk0y1"),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        LiveUpdateMessage.TYPE_WATER_FLOW.equals(message.type())));
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
