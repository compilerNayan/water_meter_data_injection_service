package com.vswitch.datainjection.device.presence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceStore;
import com.vswitch.datainjection.device.stream.DeviceLiveTelemetryStore;

@Service
public class PresenceActivityQueryService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_DAYS_PER_REQUEST = 90;
    private static final int DEFAULT_DAYS = 30;

    private final PresenceHistoryStore presenceHistoryStore;
    private final DeviceStore deviceStore;

    public PresenceActivityQueryService(
            PresenceHistoryStore presenceHistoryStore, DeviceStore deviceStore) {
        this.presenceHistoryStore = presenceHistoryStore;
        this.deviceStore = deviceStore;
    }

    public DevicePresenceActivityResponse getActivity(
            String tenantId,
            String deviceId,
            LocalDate singleDate,
            LocalDate fromDate,
            LocalDate toDate,
            Integer days,
            String timezoneOverride) {
        String normalizedDeviceId = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        ZoneId zone = resolveTimezone(normalizedDeviceId, timezoneOverride);
        LocalDate today = LocalDate.now(zone);

        LocalDate resolvedFrom;
        LocalDate resolvedTo;
        if (singleDate != null) {
            resolvedFrom = singleDate;
            resolvedTo = singleDate;
        } else if (fromDate != null && toDate != null) {
            resolvedFrom = fromDate;
            resolvedTo = toDate;
        } else {
            int dayCount = days == null ? DEFAULT_DAYS : Math.min(Math.max(days, 1), MAX_DAYS_PER_REQUEST);
            resolvedTo = today;
            resolvedFrom = today.minusDays(dayCount - 1L);
        }

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from date must be on or before to date");
        }
        long spanDays = resolvedTo.toEpochDay() - resolvedFrom.toEpochDay() + 1;
        if (spanDays > MAX_DAYS_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "Date range exceeds maximum of " + MAX_DAYS_PER_REQUEST + " days");
        }

        Instant rangeStart = resolvedFrom.atStartOfDay(zone).toInstant();
        Instant rangeEnd = resolvedTo.plusDays(1).atStartOfDay(zone).toInstant();
        Instant queryFrom = resolvedFrom.minusDays(1).atStartOfDay(zone).toInstant();

        Optional<PresenceTransitionRecord> lastBeforeRange =
                presenceHistoryStore.findLastEventBefore(
                        normalizedDeviceId,
                        PresenceTransitionRecord.formatEventAt(rangeStart));

        List<PresenceTransitionRecord> events =
                presenceHistoryStore.queryEvents(
                        normalizedDeviceId,
                        PresenceTransitionRecord.formatEventAt(queryFrom),
                        PresenceTransitionRecord.formatEventAt(rangeEnd));

        List<PresenceSegmentCalculator.DayPresenceActivity> computedDays =
                PresenceSegmentCalculator.computeDays(
                        zone,
                        resolvedFrom,
                        resolvedTo,
                        Instant.now(),
                        lastBeforeRange,
                        events);

        return new DevicePresenceActivityResponse(
                normalizedDeviceId,
                zone.getId(),
                resolvedFrom.format(DATE_FORMAT),
                resolvedTo.format(DATE_FORMAT),
                computedDays);
    }

    private ZoneId resolveTimezone(String deviceId, String timezoneOverride) {
        if (timezoneOverride != null && !timezoneOverride.isBlank()) {
            try {
                return ZoneId.of(timezoneOverride.trim());
            } catch (Exception ignored) {
                return ZoneOffset.UTC;
            }
        }
        return deviceStore
                .findDeviceConfig(deviceId)
                .map(config -> config.timezone())
                .map(
                        tz -> {
                            try {
                                return ZoneId.of(tz);
                            } catch (Exception e) {
                                return ZoneOffset.UTC;
                            }
                        })
                .orElse(ZoneOffset.UTC);
    }

    public record DevicePresenceActivityResponse(
            String deviceId,
            String timezone,
            String from,
            String to,
            List<PresenceSegmentCalculator.DayPresenceActivity> days) {}
}
