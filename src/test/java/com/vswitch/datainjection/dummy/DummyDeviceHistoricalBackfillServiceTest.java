package com.vswitch.datainjection.dummy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.CurrentReadingResponse;
import com.vswitch.datainjection.DayHistoryRecord;
import com.vswitch.datainjection.MinuteVolumeCsv;
import com.vswitch.datainjection.MockDeviceProfileFactory;
import com.vswitch.datainjection.device.ThirtyMinuteBucketPayload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DummyDeviceHistoricalBackfillServiceTest {

    @Mock private com.vswitch.datainjection.device.DeviceFacade deviceFacade;
    @Mock private ExecutorService backfillExecutor;

    private DummyDeviceHistoricalBackfillService service;

    @BeforeEach
    void setUp() {
        service =
                new DummyDeviceHistoricalBackfillService(
                        deviceFacade,
                        new MockDeviceProfileFactory(),
                        backfillExecutor,
                        1,
                        400,
                        true,
                        600,
                        1400);
    }

    @Test
    void dailyTargetLitersStaysWithinRangeAndVariesByDate() {
        LocalDate dayA = LocalDate.parse("2026-06-01");
        LocalDate dayB = LocalDate.parse("2026-06-02");

        double litersA = service.dailyTargetLiters("WM001", dayA);
        double litersB = service.dailyTargetLiters("WM001", dayB);

        assertTrue(litersA >= 600 && litersA <= 1400);
        assertTrue(litersB >= 600 && litersB <= 1400);
        assertTrue(litersA != litersB);
    }

    @Test
    void dailyTargetLitersSpansWideRangeOverMonth() {
        LocalDate start = LocalDate.parse("2026-05-01");
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 0; i < 30; i++) {
            double liters = service.dailyTargetLiters("AS146", start.plusDays(i));
            min = Math.min(min, liters);
            max = Math.max(max, liters);
        }
        assertTrue(min >= 600);
        assertTrue(max <= 1400);
        assertTrue(max - min >= 400, "expected wide day-to-day spread, got " + (max - min));
    }

    @Test
    void writesDayHistoryWithMinuteBucketsNearDailyTarget() {
        when(deviceFacade.hasDayHistory(eq("WM010"), any(LocalDate.class))).thenReturn(false);
        when(deviceFacade.hasTodaySlots(eq("WM010"), any(LocalDate.class))).thenReturn(false);
        when(deviceFacade.getCurrentReading("WM010"))
                .thenReturn(
                        new CurrentReadingResponse("WM010", Instant.now().toString(), 0, 900, "idle"));

        service.backfillIfNeeded("tenant-1", "WM010");

        ArgumentCaptor<DayHistoryRecord> captor = ArgumentCaptor.forClass(DayHistoryRecord.class);
        verify(deviceFacade).writeDayHistory(captor.capture());
        verify(deviceFacade).applyHistoricalCumulative(eq("WM010"), any(Double.class), any(Instant.class));
        verify(deviceFacade, times(completedBucketsToday())).ingest30MinuteBucket(any());

        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        double target = service.dailyTargetLiters("WM010", date);

        DayHistoryRecord record = captor.getValue();
        assertEquals("WM010", record.deviceId());
        assertEquals("tenant-1", record.tenantId());
        assertEquals(MinuteVolumeCsv.MINUTES_PER_DAY, MinuteVolumeCsv.decodeMl(record.vCsv()).length);

        double total = record.totalLiters();
        assertTrue(total >= target * 0.99 && total <= target * 1.01);
    }

    @Test
    void backfillsTodaySlotsForCompletedThirtyMinuteIntervals() {
        Instant now = Instant.parse("2026-06-15T17:45:00Z");

        service.backfillTodaySlots("tenant-1", "WM011", 12_000, now);

        ArgumentCaptor<ThirtyMinuteBucketPayload> captor =
                ArgumentCaptor.forClass(ThirtyMinuteBucketPayload.class);
        verify(deviceFacade, times(35)).ingest30MinuteBucket(captor.capture());

        List<ThirtyMinuteBucketPayload> buckets = captor.getAllValues();
        assertEquals(Instant.parse("2026-06-15T00:00:00Z"), buckets.get(0).periodStart());
        assertEquals(Instant.parse("2026-06-15T17:00:00Z"), buckets.get(34).periodStart());
        for (ThirtyMinuteBucketPayload bucket : buckets) {
            assertEquals(30, bucket.minutes().size());
        }
        assertTrue(buckets.get(34).cumulativeLiters() > 12_000);
    }

    @Test
    void lastCompletedPeriodStartAlignsToPreviousBucket() {
        ZonedDateTime atFiveFortyFive =
                ZonedDateTime.of(2026, 6, 15, 17, 45, 0, 0, ZoneOffset.UTC);
        ZonedDateTime lastCompleted =
                DummyDeviceHistoricalBackfillService.lastCompletedPeriodStart(atFiveFortyFive);

        assertEquals(
                ZonedDateTime.of(2026, 6, 15, 17, 0, 0, 0, ZoneOffset.UTC), lastCompleted);
    }

    @Test
    void skipsWhenOldestDayAlreadyExistsAndTodaySlotsPresent() {
        when(deviceFacade.hasDayHistory(eq("WM001"), any(LocalDate.class))).thenReturn(true);
        when(deviceFacade.hasTodaySlots(eq("WM001"), any(LocalDate.class))).thenReturn(true);

        service.backfillIfNeeded("tenant-1", "WM001");

        verify(deviceFacade, never()).writeDayHistory(any());
        verify(deviceFacade, never()).applyHistoricalCumulative(any(), any(Double.class), any());
        verify(deviceFacade, never()).ingest30MinuteBucket(any());
    }

    @Test
    void backfillsTodayOnlyWhenHistoryAlreadyPresent() {
        when(deviceFacade.hasDayHistory(eq("WM003"), any(LocalDate.class))).thenReturn(true);
        when(deviceFacade.hasTodaySlots(eq("WM003"), any(LocalDate.class))).thenReturn(false);
        when(deviceFacade.getCurrentReading("WM003"))
                .thenReturn(
                        new CurrentReadingResponse(
                                "WM003", Instant.now().toString(), 0, 5_000, "idle"));

        service.backfillIfNeeded("tenant-1", "WM003");

        verify(deviceFacade, never()).writeDayHistory(any());
        verify(deviceFacade, never()).applyHistoricalCumulative(any(), any(Double.class), any());
        verify(deviceFacade, times(completedBucketsToday())).ingest30MinuteBucket(any());
    }

    @Test
    void scheduleBackfillRunsOnExecutor() {
        doAnswer(
                        invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(backfillExecutor)
                .submit(any(Runnable.class));
        when(deviceFacade.hasDayHistory(eq("WM002"), any(LocalDate.class))).thenReturn(true);
        when(deviceFacade.hasTodaySlots(eq("WM002"), any(LocalDate.class))).thenReturn(true);

        service.scheduleBackfill("tenant-1", "WM002");

        verify(backfillExecutor).submit(any(Runnable.class));
        verify(deviceFacade).hasDayHistory(eq("WM002"), any(LocalDate.class));
    }

    private static int completedBucketsToday() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        ZonedDateTime dayStart = now.toLocalDate().atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime lastCompleted =
                DummyDeviceHistoricalBackfillService.lastCompletedPeriodStart(now);
        if (lastCompleted.isBefore(dayStart)) {
            return 0;
        }
        return (int) (java.time.temporal.ChronoUnit.MINUTES.between(dayStart, lastCompleted) / 30) + 1;
    }
}
