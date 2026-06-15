package com.vswitch.datainjection.device.stream;

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

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaterFlowLiveBroadcastGateTest {

    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;

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
        gate = new WaterFlowLiveBroadcastGate(liveUpdateBroadcaster, clock, scheduler);
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
        assertEquals(LiveUpdateMessage.TYPE_WATER_FLOW, message.type());
        assertEquals(149, message.cumulativeLiters(), 0.001);
        assertEquals(now.minusSeconds(1).toString(), message.ts());
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
                                        LiveUpdateMessage.TYPE_WATER_FLOW.equals(message.type())));
    }

    @Test
    void rateLimitsToOneBroadcastPerSecond() {
        gate.offer(pulse(now.minusSeconds(1), 100), 2.7);
        gate.flushNowForTests("WM001");
        gate.offer(pulse(now.minusSeconds(1), 110), 2.7);
        gate.flushNowForTests("WM001");

        verify(liveUpdateBroadcaster, times(1))
                .broadcast(
                        eq("tenant-1"),
                        org.mockito.ArgumentMatchers.argThat(
                                message ->
                                        LiveUpdateMessage.TYPE_WATER_FLOW.equals(message.type())));
    }

    private static DeviceStreamPulsePayload pulse(Instant ts, double cumulativeLiters) {
        return DeviceStreamPulsePayload.from(
                "tenant-1", "WM001", "WM001", ts, 45, cumulativeLiters);
    }
}
