package com.vswitch.datainjection.device.presence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PresenceSegmentCalculator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OFFSET_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private PresenceSegmentCalculator() {}

    public record PresenceSegment(String status, String start, String end, long durationSeconds) {}

    public record DayPresenceActivity(
            String date,
            List<PresenceSegment> segments,
            long onlineSeconds,
            long offlineSeconds) {}

    public static List<DayPresenceActivity> computeDays(
            ZoneId zone,
            LocalDate fromDate,
            LocalDate toDate,
            Instant now,
            Optional<PresenceTransitionRecord> lastBeforeRange,
            List<PresenceTransitionRecord> eventsInWindow) {
        List<PresenceTransitionRecord> sorted =
                eventsInWindow.stream()
                        .sorted(Comparator.comparing(PresenceTransitionRecord::eventInstant))
                        .toList();

        List<DayPresenceActivity> days = new ArrayList<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            Instant effectiveEnd = now.isBefore(dayEnd) ? now : dayEnd;

            boolean onlineAtStart = resolveStateAt(dayStart, lastBeforeRange, sorted);
            List<PresenceTransitionRecord> dayEvents =
                    sorted.stream()
                            .filter(
                                    event -> {
                                        Instant at = event.eventInstant();
                                        return !at.isBefore(dayStart) && at.isBefore(dayEnd);
                                    })
                            .toList();

            List<PresenceSegment> segments = new ArrayList<>();
            Instant cursor = dayStart;
            boolean currentOnline = onlineAtStart;

            for (PresenceTransitionRecord event : dayEvents) {
                Instant eventAt = event.eventInstant();
                if (eventAt.isAfter(cursor) && cursor.isBefore(effectiveEnd)) {
                    Instant segmentEnd = eventAt.isBefore(effectiveEnd) ? eventAt : effectiveEnd;
                    if (segmentEnd.isAfter(cursor)) {
                        segments.add(
                                toSegment(zone, currentOnline, cursor, segmentEnd));
                    }
                }
                currentOnline = event.isOnline();
                cursor = eventAt.isAfter(cursor) ? eventAt : cursor;
            }

            if (cursor.isBefore(effectiveEnd)) {
                segments.add(toSegment(zone, currentOnline, cursor, effectiveEnd));
            }

            long onlineSeconds = 0;
            long offlineSeconds = 0;
            for (PresenceSegment segment : segments) {
                if (PresenceTransitionRecord.STATUS_ONLINE.equals(segment.status())) {
                    onlineSeconds += segment.durationSeconds();
                } else {
                    offlineSeconds += segment.durationSeconds();
                }
            }

            days.add(
                    new DayPresenceActivity(
                            date.format(DATE_FORMAT),
                            List.copyOf(segments),
                            onlineSeconds,
                            offlineSeconds));
        }
        return days;
    }

    private static boolean resolveStateAt(
            Instant instant,
            Optional<PresenceTransitionRecord> lastBeforeRange,
            List<PresenceTransitionRecord> sortedEvents) {
        Optional<PresenceTransitionRecord> prior = Optional.empty();
        for (PresenceTransitionRecord event : sortedEvents) {
            if (event.eventInstant().isBefore(instant)) {
                prior = Optional.of(event);
            } else {
                break;
            }
        }
        if (prior.isEmpty()) {
            prior = lastBeforeRange;
        }
        return prior.map(PresenceTransitionRecord::isOnline).orElse(false);
    }

    private static PresenceSegment toSegment(
            ZoneId zone, boolean online, Instant start, Instant end) {
        String status =
                online
                        ? PresenceTransitionRecord.STATUS_ONLINE
                        : PresenceTransitionRecord.STATUS_OFFLINE;
        long seconds = Math.max(0, ChronoUnit.SECONDS.between(start, end));
        return new PresenceSegment(
                status, formatInstant(zone, start), formatInstant(zone, end), seconds);
    }

    private static String formatInstant(ZoneId zone, Instant instant) {
        return OFFSET_FORMAT.format(ZonedDateTime.ofInstant(instant, zone));
    }
}
