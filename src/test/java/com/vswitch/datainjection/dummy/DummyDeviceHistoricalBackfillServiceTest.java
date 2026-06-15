package com.vswitch.datainjection.dummy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.DayHistoryRecord;
import com.vswitch.datainjection.MinuteVolumeCsv;
import com.vswitch.datainjection.MockDeviceProfileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
                        true);
    }

    @Test
    void dailyTargetLitersStaysWithinRangeAndVariesByDate() {
        LocalDate dayA = LocalDate.parse("2026-06-01");
        LocalDate dayB = LocalDate.parse("2026-06-02");

        double litersA = DummyDeviceHistoricalBackfillService.dailyTargetLiters("WM001", dayA);
        double litersB = DummyDeviceHistoricalBackfillService.dailyTargetLiters("WM001", dayB);

        assertTrue(litersA >= 600 && litersA <= 1400);
        assertTrue(litersB >= 600 && litersB <= 1400);
        assertTrue(litersA != litersB);
    }

    @Test
    void writesDayHistoryWithMinuteBucketsNearDailyTarget() {
        when(deviceFacade.hasDayHistory(eq("WM010"), any(LocalDate.class))).thenReturn(false);

        service.backfillIfNeeded("tenant-1", "WM010");

        ArgumentCaptor<DayHistoryRecord> captor = ArgumentCaptor.forClass(DayHistoryRecord.class);
        verify(deviceFacade).writeDayHistory(captor.capture());
        verify(deviceFacade).applyHistoricalCumulative(eq("WM010"), any(Double.class), any(Instant.class));

        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        double target = DummyDeviceHistoricalBackfillService.dailyTargetLiters("WM010", date);

        DayHistoryRecord record = captor.getValue();
        assertEquals("WM010", record.deviceId());
        assertEquals("tenant-1", record.tenantId());
        assertEquals(MinuteVolumeCsv.MINUTES_PER_DAY, MinuteVolumeCsv.decodeMl(record.vCsv()).length);

        double total = record.totalLiters();
        assertTrue(total >= target * 0.8 && total <= target * 1.2);
    }

    @Test
    void skipsWhenOldestDayAlreadyExists() {
        when(deviceFacade.hasDayHistory(eq("WM001"), any(LocalDate.class))).thenReturn(true);

        service.backfillIfNeeded("tenant-1", "WM001");

        verify(deviceFacade, never()).writeDayHistory(any());
        verify(deviceFacade, never()).applyHistoricalCumulative(any(), any(Double.class), any());
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

        service.scheduleBackfill("tenant-1", "WM002");

        verify(backfillExecutor).submit(any(Runnable.class));
        verify(deviceFacade).hasDayHistory(eq("WM002"), any(LocalDate.class));
    }
}
