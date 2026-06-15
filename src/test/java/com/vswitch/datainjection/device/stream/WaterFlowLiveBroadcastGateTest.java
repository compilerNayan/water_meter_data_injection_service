package com.vswitch.datainjection.device.stream;

import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;
import com.vswitch.datainjection.live.WaterFlowTickDevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaterFlowLiveBroadcastGateTest {

    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    @Mock private DeviceMonthPrefixCache monthPrefixCache;

    private ScheduledExecutorService scheduler;
    private WaterFlowLiveBroadcastGate gate;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-06-15T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "test-water-flow-broadcast");
                            thread.setDaemon(true);
                            return thread;
                        });
        gate = new WaterFlowLiveBroadcastGate(liveUpdateBroadcaster, monthPrefixCache, clock, scheduler);
        when(monthPrefixCache.liveMonthLiters(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            Double today = invocation.getArgument(1);
                            return today == null ? 12452.0 : 12452.0 + today;
                        });
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void burstKeepsOnlyLatestPulse() {
        for (int second = 0; second < 50; second++) {
            gate.offer(
                    pulse(now.minusSeconds(50 - second), 100 + second),
                    2.7);
        }

        gate.flushNowForTests("WM001");

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster, times(1)).broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals(LiveUpdateMessage.TYPE_WATER_FLOW_TICK, message.type());
        assertEquals(1, message.devices().size());
        WaterFlowTickDevice device = message.devices().get(0);
        assertEquals("WM001", device.deviceId());
        assertEquals(149, device.cumulativeLiters(), 0.001);
        assertEquals(12.5, device.todayLiters(), 0.001);
        assertEquals(12464.5, device.monthLiters(), 0.001);
        assertEquals(now.minusSeconds(1).toString(), device.ts());
    }

    @Test
    void batchesMultipleDevicesIntoOneTick() {
        gate.offer(pulse("WM001", now.minusSeconds(1), 100), 2.7);
        gate.offer(pulse("WM002", now.minusSeconds(1), 200), 3.0);

        gate.flushNowForTests("WM001");

        ArgumentCaptor<LiveUpdateMessage> captor = ArgumentCaptor.forClass(LiveUpdateMessage.class);
        verify(liveUpdateBroadcaster, times(1)).broadcast(eq("tenant-1"), captor.capture());
        LiveUpdateMessage message = captor.getValue();
        assertEquals(LiveUpdateMessage.TYPE_WATER_FLOW_TICK, message.type());
        assertEquals(2, message.devices().size());
    }

    @Test
    void staleBurstIsNotBroadcast() {
        for (int second = 0; second < 50; second++) {
            gate.offer(
                    pulse(now.minusSeconds(60 - second), 100 + second),
                    2.7);
        }

        gate.flushNowForTests("WM001");

        verify(liveUpdateBroadcaster, never())
                .broadcast(
                        eq("tenant-1"),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        LiveUpdateMessage.TYPE_WATER_FLOW_TICK.equals(
                                                message.type())));
    }

    @Test
    void rateLimitsToOneBroadcastPerSecond() {
        gate.offer(pulse("WM001", now.minusSeconds(1), 100), 2.7);
        gate.flushNowForTests("WM001");
        gate.offer(pulse("WM001", now.minusSeconds(1), 110), 2.7);
        gate.flushNowForTests("WM001");

        verify(liveUpdateBroadcaster, times(1))
                .broadcast(
                        eq("tenant-1"),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        LiveUpdateMessage.TYPE_WATER_FLOW_TICK.equals(
                                                message.type())));
    }

    private static DeviceStreamPulsePayload pulse(Instant ts, double cumulativeLiters) {
        return pulse("WM001", ts, cumulativeLiters);
    }

    private static DeviceStreamPulsePayload pulse(
            String deviceId, Instant ts, double cumulativeLiters) {
        return DeviceStreamPulsePayload.from(
                "tenant-1", deviceId, deviceId, ts, 45, cumulativeLiters, 12.5);
    }
}
