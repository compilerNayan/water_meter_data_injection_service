package com.vswitch.datainjection.device.presence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceSegmentCalculatorTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void midnightCarryOverShowsOfflineFromMidnightUntilOnline() {
        LocalDate jun12 = LocalDate.of(2026, 6, 12);
        LocalDate jun13 = LocalDate.of(2026, 6, 13);

        Instant offlineAt =
                jun12.atTime(23, 50).atZone(IST).toInstant();
        Instant onlineAt = jun13.atTime(0, 10).atZone(IST).toInstant();
        Instant now = jun13.atTime(12, 0).atZone(IST).toInstant();

        List<PresenceTransitionRecord> events =
                List.of(
                        event(offlineAt, PresenceTransitionRecord.STATUS_OFFLINE),
                        event(onlineAt, PresenceTransitionRecord.STATUS_ONLINE));

        List<PresenceSegmentCalculator.DayPresenceActivity> jun12Days =
                PresenceSegmentCalculator.computeDays(
                        IST, jun12, jun12, now, Optional.empty(), events);
        assertEquals(1, jun12Days.size());
        PresenceSegmentCalculator.DayPresenceActivity jun12Activity = jun12Days.get(0);
        assertEquals("2026-06-12", jun12Activity.date());
        assertTrue(jun12Activity.offlineSeconds() >= 600);

        List<PresenceSegmentCalculator.DayPresenceActivity> jun13Days =
                PresenceSegmentCalculator.computeDays(
                        IST,
                        jun13,
                        jun13,
                        now,
                        Optional.of(events.get(0)),
                        events);
        assertEquals(1, jun13Days.size());
        PresenceSegmentCalculator.DayPresenceActivity jun13Activity = jun13Days.get(0);
        assertEquals(2, jun13Activity.segments().size());
        assertEquals(
                PresenceTransitionRecord.STATUS_OFFLINE, jun13Activity.segments().get(0).status());
        assertEquals(600, jun13Activity.segments().get(0).durationSeconds());
        assertEquals(
                PresenceTransitionRecord.STATUS_ONLINE, jun13Activity.segments().get(1).status());
    }

    @Test
    void allOfflineDayWhenNoPriorEvents() {
        LocalDate day = LocalDate.of(2026, 6, 12);
        Instant now = day.plusDays(1).atStartOfDay(IST).toInstant();

        List<PresenceSegmentCalculator.DayPresenceActivity> days =
                PresenceSegmentCalculator.computeDays(
                        IST, day, day, now, Optional.empty(), List.of());

        assertEquals(1, days.size());
        assertEquals(86400, days.get(0).offlineSeconds());
        assertEquals(0, days.get(0).onlineSeconds());
    }

    @Test
    void allOnlineDayFromPriorOnlineEvent() {
        LocalDate day = LocalDate.of(2026, 6, 12);
        Instant priorOnline = day.minusDays(1).atTime(20, 0).atZone(IST).toInstant();
        Instant now = day.plusDays(1).atStartOfDay(IST).toInstant();

        List<PresenceSegmentCalculator.DayPresenceActivity> days =
                PresenceSegmentCalculator.computeDays(
                        IST,
                        day,
                        day,
                        now,
                        Optional.of(event(priorOnline, PresenceTransitionRecord.STATUS_ONLINE)),
                        List.of());

        assertEquals(1, days.size());
        assertEquals(86400, days.get(0).onlineSeconds());
        assertEquals(0, days.get(0).offlineSeconds());
    }

    @Test
    void capsLastSegmentAtNowForToday() {
        LocalDate today = LocalDate.of(2026, 6, 12);
        Instant onlineAt = today.atTime(8, 0).atZone(IST).toInstant();
        Instant now = today.atTime(10, 30).atZone(IST).toInstant();

        List<PresenceSegmentCalculator.DayPresenceActivity> days =
                PresenceSegmentCalculator.computeDays(
                        IST,
                        today,
                        today,
                        now,
                        Optional.empty(),
                        List.of(event(onlineAt, PresenceTransitionRecord.STATUS_ONLINE)));

        assertEquals(1, days.size());
        assertEquals(2 * 3600 + 30 * 60, days.get(0).onlineSeconds());
    }

    private static PresenceTransitionRecord event(Instant at, String status) {
        return new PresenceTransitionRecord(
                "WM001",
                PresenceTransitionRecord.formatEventAt(at),
                "tenant-1",
                status,
                PresenceTransitionRecord.SOURCE_HEARTBEAT,
                at.getEpochSecond() + 365 * 86400L);
    }
}
