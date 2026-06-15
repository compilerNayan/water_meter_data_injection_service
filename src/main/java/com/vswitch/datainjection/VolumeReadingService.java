package com.vswitch.datainjection;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceStore;
import com.vswitch.datainjection.device.TodaySlotRecord;

@Service
public class VolumeReadingService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeviceStore deviceStore;

    public VolumeReadingService(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    MinutesTodayResponse getTodayMinutes(String deviceId, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        int[] milliliters = loadDayMilliliters(deviceId, today, zone);
        int[] slice = MinuteVolumeCsv.sliceToNow(milliliters, zone);
        Instant startAt = today.atStartOfDay(zone).toInstant();

        return new MinutesTodayResponse(
                deviceId,
                today.format(DATE_FORMAT),
                timezone,
                1,
                startAt.toString(),
                MinuteVolumeCsv.toLiters(slice));
    }

    MinutesHistoryResponse getHistoryMinutes(String deviceId, int days, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(Math.max(1, days) - 1L);

        List<MinutesDayResponse> dayResponses = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            int[] milliliters = loadDayMilliliters(deviceId, date, zone);
            if (date.equals(today)) {
                milliliters = MinuteVolumeCsv.sliceToNow(milliliters, zone);
            }
            dayResponses.add(
                    new MinutesDayResponse(
                            date.format(DATE_FORMAT),
                            date.atStartOfDay(zone).toInstant().toString(),
                            MinuteVolumeCsv.toLiters(milliliters)));
        }

        return new MinutesHistoryResponse(deviceId, timezone, 1, dayResponses);
    }

    public double getTodayUsedLiters(String deviceId, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        int[] milliliters = loadDayMilliliters(deviceId, today, zone);
        int[] slice = MinuteVolumeCsv.sliceToNow(milliliters, zone);
        return MinuteVolumeCsv.sumLiters(slice);
    }

    public double sumMonthLiters(String deviceId, String timezone) {
        return sumLitersThroughYesterday(deviceId, timezone)
                + getTodayUsedLiters(deviceId, timezone);
    }

    /** Sum of completed days in the current calendar month (device timezone), excluding today. */
    public double sumLitersThroughYesterday(String deviceId, String timezone) {
        ZoneId zone = safeZone(timezone);
        LocalDate today = LocalDate.now(zone);
        YearMonth month = YearMonth.from(today);
        double total = 0;
        for (LocalDate date = month.atDay(1); date.isBefore(today); date = date.plusDays(1)) {
            total += litersForCompletedDay(deviceId, date);
        }
        return total;
    }

    DailySummaryResponse getDailySummary(
            String deviceId, LocalDate from, LocalDate to, String timezone) {
        ZoneId zone = safeZone(timezone);
        List<DailySummaryDayResponse> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int[] milliliters = loadDayMilliliters(deviceId, date, zone);
            if (date.equals(LocalDate.now(zone))) {
                milliliters = MinuteVolumeCsv.sliceToNow(milliliters, zone);
            }
            double total = MinuteVolumeCsv.sumLiters(milliliters);
            int peakHour = MinuteVolumeCsv.peakHour(padDay(milliliters));
            double peakHourLiters = MinuteVolumeCsv.peakHourLiters(padDay(milliliters), peakHour);
            days.add(
                    new DailySummaryDayResponse(
                            date.format(DATE_FORMAT), total, peakHour, peakHourLiters));
        }
        return new DailySummaryResponse("liters", days);
    }

    HourlyPatternResponse getHourlyPattern(
            String deviceId, LocalDate from, LocalDate to, String timezone) {
        ZoneId zone = safeZone(timezone);
        double[] hourTotals = new double[24];
        int dayCount = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int[] milliliters = loadDayMilliliters(deviceId, date, zone);
            if (date.equals(LocalDate.now(zone))) {
                milliliters = MinuteVolumeCsv.sliceToNow(milliliters, zone);
            }
            if (milliliters.length == 0) {
                continue;
            }
            dayCount++;
            double[] dayHourly = MinuteVolumeCsv.hourlyAverages(padDay(milliliters));
            for (int hour = 0; hour < 24; hour++) {
                hourTotals[hour] += dayHourly[hour];
            }
        }

        List<HourlyPatternHourResponse> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            double avg = dayCount == 0 ? 0 : hourTotals[hour] / dayCount;
            hours.add(new HourlyPatternHourResponse(hour, avg));
        }
        return new HourlyPatternResponse("liters", hours);
    }

    WaterUsageResponse getUsage(
            String deviceId, Instant from, Instant to, String granularity, String timezone) {
        UsageGranularity g = UsageGranularity.fromApiValue(granularity);
        ZoneId zone = safeZone(timezone);
        List<UsageDataPointResponse> points = aggregateRange(deviceId, from, to, g, zone);

        double total = points.stream().mapToDouble(UsageDataPointResponse::volumeLiters).sum();
        double avg = points.isEmpty() ? 0 : total / points.size();
        UsageDataPointResponse peak =
                points.stream()
                        .max(Comparator.comparingDouble(UsageDataPointResponse::volumeLiters))
                        .orElse(new UsageDataPointResponse(from.toString(), 0, 0));

        Duration range = Duration.between(from, to);
        Instant prevFrom = from.minus(range);
        List<UsageDataPointResponse> prevPoints =
                aggregateRange(deviceId, prevFrom, from, g, zone);
        double prevTotal =
                prevPoints.stream().mapToDouble(UsageDataPointResponse::volumeLiters).sum();
        double deltaPercent =
                prevTotal <= 0 ? 0 : ((total - prevTotal) / prevTotal) * 100.0;

        return new WaterUsageResponse(
                deviceId,
                from.toString(),
                to.toString(),
                g.apiValue(),
                "liters",
                points,
                new UsageSummaryResponse(
                        total,
                        avg,
                        new PeakBucketResponse(peak.timestamp(), peak.volumeLiters()),
                        prevTotal,
                        deltaPercent));
    }

    public double litersForCompletedDay(String deviceId, LocalDate date) {
        return deviceStore
                .findDayHistory(deviceId, date)
                .map(
                        history -> {
                            if (history.totalLiters() > 0) {
                                return history.totalLiters();
                            }
                            if (history.vCsv() != null && !history.vCsv().isBlank()) {
                                return MinuteVolumeCsv.sumLiters(
                                        MinuteVolumeCsv.decodeMl(history.vCsv()));
                            }
                            return 0.0;
                        })
                .orElse(0.0);
    }

    int[] loadDayMilliliters(String deviceId, LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (date.equals(today)) {
            String localDate = DeviceStore.formatDate(date);
            List<TodaySlotRecord> slots =
                    deviceStore.queryTodaySlotsByLocalDate(deviceId, localDate);
            return MinuteVolumeCsv.stitchDayFromSlots(slots, zone);
        }
        Optional<DayHistoryRecord> history = deviceStore.findDayHistory(deviceId, date);
        return history.map(h -> MinuteVolumeCsv.decodeMl(h.vCsv())).orElse(new int[0]);
    }

    private List<UsageDataPointResponse> aggregateRange(
            String deviceId,
            Instant from,
            Instant to,
            UsageGranularity granularity,
            ZoneId zone) {
        LocalDate startDate = from.atZone(zone).toLocalDate();
        LocalDate endDate = to.atZone(zone).toLocalDate();
        List<double[]> minuteSeries = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int[] milliliters = loadDayMilliliters(deviceId, date, zone);
            if (milliliters.length == 0) {
                continue;
            }
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            for (int i = 0; i < milliliters.length; i++) {
                Instant instant = dayStart.plus(i, ChronoUnit.MINUTES);
                if (instant.isBefore(from) || instant.isAfter(to)) {
                    continue;
                }
                minuteSeries.add(new double[] {instant.toEpochMilli(), milliliters[i] / 1000.0});
            }
        }

        Duration bucket = granularity.bucketDuration();
        Map<Instant, double[]> buckets = new HashMap<>();
        for (double[] point : minuteSeries) {
            Instant instant = Instant.ofEpochMilli((long) point[0]);
            Instant bucketStart =
                    truncateToBucket(instant.atZone(zone), bucket).toInstant();
            double[] agg = buckets.computeIfAbsent(bucketStart, k -> new double[] {0, 0});
            agg[0] += point[1];
            agg[1] += 1;
        }

        List<Instant> sorted = new ArrayList<>(buckets.keySet());
        sorted.sort(Comparator.naturalOrder());

        List<UsageDataPointResponse> points = new ArrayList<>();
        for (Instant bucketStart : sorted) {
            double[] agg = buckets.get(bucketStart);
            points.add(
                    new UsageDataPointResponse(
                            bucketStart.toString(), agg[0], agg[0] / Math.max(1, agg[1])));
        }
        return points;
    }

    private static int[] padDay(int[] milliliters) {
        if (milliliters.length >= MinuteVolumeCsv.MINUTES_PER_DAY) {
            return milliliters;
        }
        int[] padded = new int[MinuteVolumeCsv.MINUTES_PER_DAY];
        System.arraycopy(milliliters, 0, padded, 0, milliliters.length);
        return padded;
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private static java.time.ZonedDateTime truncateToBucket(
            java.time.ZonedDateTime time, Duration bucket) {
        long bucketSeconds = bucket.getSeconds();
        long epoch = time.toEpochSecond();
        long truncated = (epoch / bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(truncated).atZone(time.getZone());
    }
}
