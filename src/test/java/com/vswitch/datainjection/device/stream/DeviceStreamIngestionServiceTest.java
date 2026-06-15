package com.vswitch.datainjection.device.stream;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;

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
    private WaterFlowLiveBroadcastGate broadcastGate;
    private DeviceStreamIngestionService service;

    @BeforeEach
    void setUp() {
        store = new DeviceLiveTelemetryStore();
        presenceService = new DevicePresenceService(liveUpdateBroadcaster);
        broadcastGate =
                new WaterFlowLiveBroadcastGate(
                        liveUpdateBroadcaster,
                        Clock.fixed(Instant.parse("2026-06-13T10:00:05Z"), ZoneOffset.UTC),
                        Executors.newSingleThreadScheduledExecutor());
        service =
                new DeviceStreamIngestionService(store, presenceService, broadcastGate, deviceFacade);
    }

    @Test
    void storesPulseAndBroadcastsWhenMlPositive() {
        Instant ts = Instant.parse("2026-06-13T10:00:05Z");
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1", "QJPDXN094", null, ts, 45, 123.456));

        verify(deviceFacade).ingestSecondPulse("63tk0y1", "QJPDXN094", ts, 45);

        DeviceLiveTelemetrySnapshot snapshot =
                store.find("QJPDXN094").orElseThrow();
        assertEquals("63tk0y1", snapshot.tenantId());
        assertEquals("QJPDXN094", snapshot.deviceId());
        assertEquals(45, snapshot.ml());
        assertEquals(123.456, snapshot.cumulativeLiters(), 0.001);
        assertEquals(2.7, snapshot.flowRateLpm(), 0.001);

        broadcastGate.flushNowForTests("QJPDXN094");

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster, org.mockito.Mockito.atLeastOnce())
                .broadcast(eq("63tk0y1"), captor.capture());
        LiveUpdateMessage message =
                captor.getAllValues().stream()
                        .filter(item -> LiveUpdateMessage.TYPE_WATER_FLOW_TICK.equals(item.type()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("QJPDXN094", message.devices().get(0).deviceId());
        assertEquals(45, message.devices().get(0).ml());
        assertEquals(123.456, message.devices().get(0).cumulativeLiters());
    }

    @Test
    void storesPulseWithoutBroadcastingWaterFlowWhenMlZero() {
        Instant ts = Instant.parse("2026-06-13T10:00:05Z");
        service.ingestPulse(
                DeviceStreamPulsePayload.from(
                        "63tk0y1",
                        "QJPDXN094",
                        null,
                        ts,
                        0,
                        120.0));

        verify(deviceFacade).touchHeartbeat("63tk0y1", "QJPDXN094", ts);

        assertTrue(store.find("QJPDXN094").isPresent());
        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster).broadcast(eq("63tk0y1"), captor.capture());
        assertEquals(LiveUpdateMessage.TYPE_DEVICE_PRESENCE, captor.getValue().type());
        verify(liveUpdateBroadcaster, never())
                .broadcast(
                        eq("63tk0y1"),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        LiveUpdateMessage.TYPE_WATER_FLOW_TICK.equals(
                                                message.type())));
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
