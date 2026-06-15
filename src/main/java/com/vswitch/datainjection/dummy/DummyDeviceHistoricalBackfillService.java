package com.vswitch.datainjection.dummy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.DayHistoryRecord;
import com.vswitch.datainjection.MinuteVolumeCsv;
import com.vswitch.datainjection.MockDeviceProfileFactory;
import com.vswitch.datainjection.device.DeviceFacade;

/**
 * Seeds {@link DayHistoryRecord} with the last N days of synthetic usage when a dummy device is
 * enrolled. Each day stores {@link MinuteVolumeCsv#MINUTES_PER_DAY} minute-level milliliter
 * buckets (1440 per day); daily totals are randomized between 600 L and 1400 L.
 */
@Service
public class DummyDeviceHistoricalBackfillService {

    private static final Logger log =
            LoggerFactory.getLogger(DummyDeviceHistoricalBackfillService.class);

    private final DeviceFacade deviceFacade;
    private final MockDeviceProfileFactory profileFactory;
    private final ExecutorService backfillExecutor;
    private final int backfillDays;
    private final long historyTtlSeconds;
    private final boolean enabled;
    private final double minDailyLiters;
    private final double maxDailyLiters;

    DummyDeviceHistoricalBackfillService(
            DeviceFacade deviceFacade,
            MockDeviceProfileFactory profileFactory,
            @Qualifier("dummyDeviceBackfillExecutor") ExecutorService dummyDeviceBackfillExecutor,
            @Value("${dummy.history.backfill.days:30}") int backfillDays,
            @Value("${day.history.ttl.days:400}") int historyTtlDays,
            @Value("${dummy.history.backfill.enabled:true}") boolean enabled,
            @Value("${dummy.history.backfill.daily.min-liters:600}") double minDailyLiters,
            @Value("${dummy.history.backfill.daily.max-liters:1400}") double maxDailyLiters) {
        this.deviceFacade = deviceFacade;
        this.profileFactory = profileFactory;
        this.backfillExecutor = dummyDeviceBackfillExecutor;
        this.backfillDays = Math.max(1, backfillDays);
        this.historyTtlSeconds = Math.max(30L, historyTtlDays) * 24 * 3600;
        this.enabled = enabled;
        this.minDailyLiters = Math.min(minDailyLiters, maxDailyLiters);
        this.maxDailyLiters = Math.max(minDailyLiters, maxDailyLiters);
    }

    public void scheduleBackfill(String tenantId, String deviceId) {
        if (!enabled) {
            return;
        }
        String tenant = tenantId.trim();
        String serial = deviceId.trim();
        backfillExecutor.submit(
                () -> {
                    try {
                        backfillIfNeeded(tenant, serial);
                    } catch (Exception e) {
                        log.warn(
                                "Dummy history backfill failed for {}/{}",
                                tenant,
                                serial,
                                e);
                    }
                });
    }

    void backfillIfNeeded(String tenantId, String deviceId) {
        LocalDate oldestDay = LocalDate.now(ZoneOffset.UTC).minusDays(backfillDays);
        if (deviceFacade.hasDayHistory(deviceId, oldestDay)) {
            log.debug("Dummy history already present for device {}", deviceId);
            return;
        }

        log.info(
                "Backfilling {} days of dummy history for device {}/{}",
                backfillDays,
                tenantId,
                deviceId);

        deviceFacade.initializeDeviceConfig(deviceId, tenantId);
        deviceFacade.initializeDeviceState(deviceId, tenantId);

        double totalHistoricalLiters = 0;
        Instant lastDay = null;
        long expiresAt = Instant.now().getEpochSecond() + historyTtlSeconds;

        for (int dayOffset = backfillDays; dayOffset >= 1; dayOffset--) {
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(dayOffset);
            double dailyTarget = dailyTargetLiters(deviceId, date);
            int[] milliliters = minuteVolumesForDay(deviceId, date, dailyTarget);
            double totalLiters = MinuteVolumeCsv.sumLiters(milliliters);

            deviceFacade.writeDayHistory(
                    new DayHistoryRecord(
                            deviceId,
                            DayHistoryRecord.dayKeyFor(date),
                            tenantId,
                            MinuteVolumeCsv.encodeMl(milliliters),
                            totalLiters,
                            "UTC",
                            expiresAt));

            totalHistoricalLiters += totalLiters;
            lastDay = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        if (lastDay != null) {
            deviceFacade.applyHistoricalCumulative(deviceId, totalHistoricalLiters, lastDay);
        }

        log.info(
                "Finished dummy history backfill for {}/{} ({} L over {} days)",
                tenantId,
                deviceId,
                totalHistoricalLiters,
                backfillDays);
    }

    double dailyTargetLiters(String deviceId, LocalDate date) {
        long seed =
                mix64(
                        ((long) deviceId.hashCode() << 32)
                                ^ date.toEpochDay()
                                ^ ((long) date.getDayOfYear() << 16)
                                ^ deviceId.length());
        double unit = Long.remainderUnsigned(seed, 1_000_000L) / 1_000_000.0;
        double raw = minDailyLiters + unit * (maxDailyLiters - minDailyLiters);
        return Math.round(raw * 10.0) / 10.0;
    }

    private int[] minuteVolumesForDay(String deviceId, LocalDate date, double dailyTarget) {
        double[] hourVolumes = hourlyVolumesForDay(deviceId, date, dailyTarget);
        int[] milliliters = new int[MinuteVolumeCsv.MINUTES_PER_DAY];

        for (int hour = 0; hour < 24; hour++) {
            double hourLiters = hourVolumes[hour];
            double perMinute = hourLiters / 60.0;
            int start = hour * 60;
            for (int minute = 0; minute < 60; minute++) {
                double noise = 0.85 + pseudoRandom(deviceId, date, hour, minute) * 0.3;
                milliliters[start + minute] = (int) Math.round(perMinute * noise * 1000);
            }
        }
        return scaleToTargetLiters(milliliters, dailyTarget);
    }

    private static int[] scaleToTargetLiters(int[] milliliters, double dailyTarget) {
        double sumLiters = MinuteVolumeCsv.sumLiters(milliliters);
        if (sumLiters <= 0) {
            return milliliters;
        }
        double factor = dailyTarget / sumLiters;
        int targetMl = (int) Math.round(dailyTarget * 1000);
        int runningMl = 0;
        for (int i = 0; i < milliliters.length; i++) {
            if (i == milliliters.length - 1) {
                milliliters[i] = Math.max(0, targetMl - runningMl);
            } else {
                milliliters[i] = Math.max(0, (int) Math.round(milliliters[i] * factor));
                runningMl += milliliters[i];
            }
        }
        return milliliters;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private double[] hourlyVolumesForDay(String deviceId, LocalDate date, double dailyTarget) {
        double[] weights = new double[24];
        double weekendFactor = date.getDayOfWeek().getValue() >= 6 ? 1.1 : 1.0;
        for (int hour = 0; hour < 24; hour++) {
            double noise = 0.9 + pseudoRandom(deviceId, date, hour, 0) * 0.2;
            weights[hour] = profileFactory.hourlyPatternLiters(hour) * weekendFactor * noise;
        }

        double sum = Arrays.stream(weights).sum();
        double[] volumes = new double[24];
        for (int hour = 0; hour < 24; hour++) {
            volumes[hour] = dailyTarget * (weights[hour] / sum);
        }
        return volumes;
    }

    private static double pseudoRandom(String deviceId, LocalDate date, int hour, int minute) {
        int mixed = deviceId.hashCode() ^ date.hashCode() ^ (hour * 31) ^ minute;
        return (mixed & 0xFFFF) / 65535.0;
    }
}
