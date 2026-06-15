package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveWaterFlowBroadcastPolicyTest {

    private final Instant now = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void rejectsPulseTenSecondsOrOlder() {
        assertTrue(LiveWaterFlowBroadcastPolicy.isPulseTooOld(now.minusSeconds(10), now));
        assertTrue(LiveWaterFlowBroadcastPolicy.isPulseTooOld(now.minusSeconds(11), now));
    }

    @Test
    void acceptsFreshPulse() {
        assertFalse(LiveWaterFlowBroadcastPolicy.isPulseTooOld(now.minusSeconds(9), now));
        assertFalse(LiveWaterFlowBroadcastPolicy.isPulseTooOld(now, now));
    }

    @Test
    void keepLatestByTimestamp() {
        PendingWaterFlow older =
                new PendingWaterFlow(
                        DeviceStreamPulsePayload.from(
                                "t", "WM001", "WM001", now.minusSeconds(2), 10, 100),
                        1.0);
        PendingWaterFlow newer =
                new PendingWaterFlow(
                        DeviceStreamPulsePayload.from(
                                "t", "WM001", "WM001", now.minusSeconds(1), 10, 200),
                        2.0);

        PendingWaterFlow kept = LiveWaterFlowBroadcastPolicy.keepLatest(older, newer);
        assertEquals(200, kept.payload().cumulativeLiters(), 0.001);
    }
}
