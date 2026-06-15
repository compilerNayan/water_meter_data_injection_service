package com.vswitch.datainjection.dummy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.vswitch.datainjection.device.MinuteBucketEntry;
import com.vswitch.datainjection.device.ThirtyMinuteBucketPayload;

final class DummyDeviceTelemetrySession {

    private final String tenantId;
    private final String serialNumber;
    private final Random random;
    private final int minMl;
    private final int maxMl;

    private Instant periodStart;
    private int cycleSecond;
    private double minuteAccumulatorMl;
    private Instant currentMinuteStart;
    private final List<MinuteBucketEntry> periodMinutes = new ArrayList<>();
    private double cumulativeLiters;
    private double todayLiters;
    private LocalDate todayUtc;

    DummyDeviceTelemetrySession(
            String tenantId,
            String serialNumber,
            int minMl,
            int maxMl,
            Instant startedAt) {
        this.tenantId = tenantId;
        this.serialNumber = serialNumber;
        this.minMl = minMl;
        this.maxMl = maxMl;
        this.random = new Random(serialNumber.hashCode() ^ tenantId.hashCode());
        this.cycleSecond = DummyTelemetryPolicy.initialCycleSecond(tenantId, serialNumber);
        this.periodStart = alignPeriodStart(startedAt);
        this.currentMinuteStart = startedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    String deviceKey() {
        return tenantId + "#" + serialNumber;
    }

    String tenantId() {
        return tenantId;
    }

    String serialNumber() {
        return serialNumber;
    }

    double cumulativeLiters() {
        return cumulativeLiters;
    }

    double todayLiters() {
        return todayLiters;
    }

    TickResult tick(Instant now) {
        TickResult.Builder result = TickResult.builder();
        rollTodayIfNeeded(now);

        if (cycleSecond < DummyTelemetryPolicy.PULSES_PER_ACTIVE_MINUTE) {
            double ml = minMl + random.nextInt(maxMl - minMl + 1);
            cumulativeLiters += ml / 1000.0;
            todayLiters += ml / 1000.0;
            minuteAccumulatorMl += ml;
            result.pulseMl(ml);
            result.pulseTimestamp(now.truncatedTo(ChronoUnit.SECONDS));
            result.cumulativeLiters(cumulativeLiters);
            result.todayLiters(todayLiters);
        } else {
            result.pulseMl(0);
            result.pulseTimestamp(now.truncatedTo(ChronoUnit.SECONDS));
            result.cumulativeLiters(cumulativeLiters);
            result.todayLiters(todayLiters);
        }

        if (cycleSecond == DummyTelemetryPolicy.PULSES_PER_ACTIVE_MINUTE - 1) {
            periodMinutes.add(new MinuteBucketEntry(currentMinuteStart, minuteAccumulatorMl));
            minuteAccumulatorMl = 0;
            currentMinuteStart = now.plusSeconds(DummyTelemetryPolicy.IDLE_SECONDS_AFTER_MINUTE + 1L)
                    .truncatedTo(ChronoUnit.SECONDS);

            if (periodMinutes.size() >= DummyTelemetryPolicy.MINUTES_PER_BUCKET) {
                result.bucket(
                        new ThirtyMinuteBucketPayload(
                                tenantId,
                                serialNumber,
                                periodStart,
                                List.copyOf(periodMinutes),
                                cumulativeLiters,
                                DummyTelemetryPolicy.DEFAULT_VALVE_TARGET_PERCENT));
                periodMinutes.clear();
                periodStart = alignPeriodStart(now);
            }
        }

        cycleSecond = (cycleSecond + 1) % DummyTelemetryPolicy.SECONDS_PER_CYCLE;
        return result.build();
    }

    private void rollTodayIfNeeded(Instant now) {
        LocalDate date = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (todayUtc == null || !todayUtc.equals(date)) {
            todayUtc = date;
            todayLiters = 0;
        }
    }

    private static Instant alignPeriodStart(Instant instant) {
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
        int minute = zdt.getMinute();
        int alignedMinute = (minute / DummyTelemetryPolicy.MINUTES_PER_BUCKET)
                * DummyTelemetryPolicy.MINUTES_PER_BUCKET;
        return zdt.withMinute(alignedMinute).withSecond(0).withNano(0).toInstant();
    }

    static final class TickResult {
        private final Double pulseMl;
        private final Instant pulseTimestamp;
        private final Double cumulativeLiters;
        private final Double todayLiters;
        private final ThirtyMinuteBucketPayload bucket;

        private TickResult(
                Double pulseMl,
                Instant pulseTimestamp,
                Double cumulativeLiters,
                Double todayLiters,
                ThirtyMinuteBucketPayload bucket) {
            this.pulseMl = pulseMl;
            this.pulseTimestamp = pulseTimestamp;
            this.cumulativeLiters = cumulativeLiters;
            this.todayLiters = todayLiters;
            this.bucket = bucket;
        }

        static Builder builder() {
            return new Builder();
        }

        boolean hasPulse() {
            return pulseMl != null && pulseTimestamp != null && cumulativeLiters != null;
        }

        boolean hasBucket() {
            return bucket != null;
        }

        double pulseMl() {
            return pulseMl;
        }

        Instant pulseTimestamp() {
            return pulseTimestamp;
        }

        double cumulativeLiters() {
            return cumulativeLiters;
        }

        double todayLiters() {
            return todayLiters;
        }

        ThirtyMinuteBucketPayload bucket() {
            return bucket;
        }

        static final class Builder {
            private Double pulseMl;
            private Instant pulseTimestamp;
            private Double cumulativeLiters;
            private Double todayLiters;
            private ThirtyMinuteBucketPayload bucket;

            Builder pulseMl(double value) {
                this.pulseMl = value;
                return this;
            }

            Builder pulseTimestamp(Instant value) {
                this.pulseTimestamp = value;
                return this;
            }

            Builder cumulativeLiters(double value) {
                this.cumulativeLiters = value;
                return this;
            }

            Builder todayLiters(double value) {
                this.todayLiters = value;
                return this;
            }

            Builder bucket(ThirtyMinuteBucketPayload value) {
                this.bucket = value;
                return this;
            }

            TickResult build() {
                return new TickResult(pulseMl, pulseTimestamp, cumulativeLiters, todayLiters, bucket);
            }
        }
    }
}
