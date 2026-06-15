package com.vswitch.datainjection.dummy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyDeviceTelemetrySessionTest {

    @Test
    void emitsPulseEverySecondIncludingIdleHeartbeats() {
        DummyDeviceTelemetrySession session =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM001", 10, 80, Instant.parse("2026-06-15T10:00:00Z"));

        Instant start = Instant.parse("2026-06-15T10:00:00Z");
        int flowPulseCount = 0;
        int heartbeatCount = 0;
        for (int i = 0; i < 70; i++) {
            var result = session.tick(start.plusSeconds(i));
            assertTrue(result.hasPulse());
            if (result.pulseMl() > 0) {
                flowPulseCount++;
                assertTrue(result.pulseMl() >= 10 && result.pulseMl() <= 80);
            } else {
                heartbeatCount++;
            }
        }

        assertEquals(60, flowPulseCount);
        assertEquals(10, heartbeatCount);
    }

    @Test
    void accumulatesTodayLitersAcrossPulses() {
        DummyDeviceTelemetrySession session =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM001", 1000, 1000, Instant.parse("2026-06-15T10:00:00Z"));

        Instant start = Instant.parse("2026-06-15T10:00:00Z");
        var first = session.tick(start);
        var second = session.tick(start.plusSeconds(1));

        assertEquals(1.0, first.todayLiters(), 0.001);
        assertEquals(2.0, second.todayLiters(), 0.001);
    }

    @Test
    void accumulatesThirtyMinuteBucketAfterThirtyActiveMinutes() {
        DummyDeviceTelemetrySession session =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM002", 10, 10, Instant.parse("2026-06-15T10:00:00Z"));

        Instant start = Instant.parse("2026-06-15T10:00:00Z");
        DummyDeviceTelemetrySession.TickResult bucketResult = null;
        for (int cycle = 0; cycle < 30; cycle++) {
            for (int second = 0; second < 70; second++) {
                var result = session.tick(start.plusSeconds(cycle * 70L + second));
                if (result.hasBucket()) {
                    bucketResult = result;
                }
            }
        }

        assertTrue(bucketResult != null && bucketResult.hasBucket());
        assertEquals("tenant-1", bucketResult.bucket().tenantId());
        assertEquals("WM002", bucketResult.bucket().deviceId());
        assertEquals(30, bucketResult.bucket().minutes().size());
        double totalMinuteMl =
                bucketResult.bucket().minutes().stream().mapToDouble(m -> m.ml()).sum();
        assertEquals(18_000.0, totalMinuteMl, 0.001);
        assertEquals(18.0, bucketResult.bucket().cumulativeLiters(), 0.001);
    }

    @Test
    void devicesProduceDifferentMlSequences() {
        DummyDeviceTelemetrySession first =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM001", 10, 80, Instant.parse("2026-06-15T10:00:00Z"));
        DummyDeviceTelemetrySession second =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM002", 10, 80, Instant.parse("2026-06-15T10:00:00Z"));

        Instant now = Instant.parse("2026-06-15T10:00:00Z");
        boolean differs = false;
        for (int i = 0; i < 10; i++) {
            var a = first.tick(now.plusSeconds(i));
            var b = second.tick(now.plusSeconds(i));
            assertTrue(a.hasPulse() && b.hasPulse());
            if (a.pulseMl() != b.pulseMl()) {
                differs = true;
                break;
            }
        }
        assertTrue(differs);
    }

    @Test
    void devicesHaveStaggeredIdlePhases() {
        DummyDeviceTelemetrySession first =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM001", 10, 80, Instant.parse("2026-06-15T10:00:00Z"));
        DummyDeviceTelemetrySession second =
                new DummyDeviceTelemetrySession(
                        "tenant-1", "WM002", 10, 80, Instant.parse("2026-06-15T10:00:00Z"));

        assertNotEquals(
                DummyTelemetryPolicy.initialCycleSecond("tenant-1", "WM001"),
                DummyTelemetryPolicy.initialCycleSecond("tenant-1", "WM002"));

        Instant now = Instant.parse("2026-06-15T10:00:00Z");
        boolean sawDifferentPhase = false;
        for (int i = 0; i < DummyTelemetryPolicy.SECONDS_PER_CYCLE; i++) {
            var a = first.tick(now.plusSeconds(i));
            var b = second.tick(now.plusSeconds(i));
            if ((a.pulseMl() == 0) != (b.pulseMl() == 0)) {
                sawDifferentPhase = true;
                break;
            }
        }
        assertTrue(sawDifferentPhase, "devices should not idle and flow in lockstep");
    }
}
