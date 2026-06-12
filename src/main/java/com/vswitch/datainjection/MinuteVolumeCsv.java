package com.vswitch.datainjection;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.vswitch.datainjection.device.TodaySlotRecord;

/** Comma-separated milliliter arrays for day/slot storage. */
public final class MinuteVolumeCsv {

    public static final int MINUTES_PER_DAY = 1440;
    public static final int MINUTES_PER_SLOT = 30;

    private MinuteVolumeCsv() {}

    public static String encodeMl(int[] milliliters) {
        StringBuilder sb = new StringBuilder(milliliters.length * 4);
        for (int i = 0; i < milliliters.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(milliliters[i]);
        }
        return sb.toString();
    }

    public static int[] decodeMl(String csv) {
        if (csv == null || csv.isBlank()) {
            return new int[0];
        }
        String[] parts = csv.split(",", -1);
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = parts[i].isBlank() ? 0 : Integer.parseInt(parts[i].trim());
        }
        return values;
    }

    public static double[] toLiters(int[] milliliters) {
        double[] liters = new double[milliliters.length];
        for (int i = 0; i < milliliters.length; i++) {
            liters[i] = milliliters[i] / 1000.0;
        }
        return liters;
    }

    public static double sumLiters(int[] milliliters) {
        long totalMl = 0;
        for (int ml : milliliters) {
            totalMl += ml;
        }
        return totalMl / 1000.0;
    }

    public static int[] stitchDayFromSlots(List<TodaySlotRecord> slots, ZoneId zone) {
        int[] day = new int[MINUTES_PER_DAY];
        slots.stream()
                .sorted(Comparator.comparing(TodaySlotRecord::slotKey))
                .forEach(
                        slot -> {
                            Instant periodStart = TodaySlotRecord.parsePeriodStart(slot.slotKey());
                            int minuteOfDay =
                                    (int)
                                            Duration.between(
                                                            periodStart
                                                                    .atZone(zone)
                                                                    .toLocalDate()
                                                                    .atStartOfDay(zone),
                                                            periodStart.atZone(zone))
                                                    .toMinutes();
                            int[] values = decodeMl(slot.vCsv());
                            for (int i = 0;
                                    i < values.length && minuteOfDay + i < MINUTES_PER_DAY;
                                    i++) {
                                day[minuteOfDay + i] = values[i];
                            }
                        });
        return day;
    }

    public static int[] sliceToNow(int[] fullDay, ZoneId zone) {
        int minuteOfDay =
                (int)
                        Duration.between(
                                        java.time.LocalDate.now(zone).atStartOfDay(zone),
                                        Instant.now().atZone(zone))
                                .toMinutes();
        if (minuteOfDay <= 0) {
            return new int[0];
        }
        int length = Math.min(minuteOfDay, fullDay.length);
        return Arrays.copyOf(fullDay, length);
    }

    public static int peakHour(int[] dayMilliliters) {
        int peakHour = 0;
        long peakMl = 0;
        for (int hour = 0; hour < 24; hour++) {
            long hourMl = 0;
            int start = hour * 60;
            int end = start + 60;
            for (int i = start; i < end && i < dayMilliliters.length; i++) {
                hourMl += dayMilliliters[i];
            }
            if (hourMl > peakMl) {
                peakMl = hourMl;
                peakHour = hour;
            }
        }
        return peakHour;
    }

    public static double peakHourLiters(int[] dayMilliliters, int hour) {
        long hourMl = 0;
        int start = hour * 60;
        int end = start + 60;
        for (int i = start; i < end && i < dayMilliliters.length; i++) {
            hourMl += dayMilliliters[i];
        }
        return hourMl / 1000.0;
    }

    public static double[] hourlyAverages(int[] dayMilliliters) {
        double[] hourTotals = new double[24];
        int[] hourCounts = new int[24];
        for (int i = 0; i < dayMilliliters.length; i++) {
            int hour = i / 60;
            hourTotals[hour] += dayMilliliters[i] / 1000.0;
            hourCounts[hour]++;
        }
        double[] averages = new double[24];
        for (int hour = 0; hour < 24; hour++) {
            averages[hour] =
                    hourCounts[hour] == 0 ? 0 : hourTotals[hour] / Math.max(1, hourCounts[hour]);
        }
        return averages;
    }
}
