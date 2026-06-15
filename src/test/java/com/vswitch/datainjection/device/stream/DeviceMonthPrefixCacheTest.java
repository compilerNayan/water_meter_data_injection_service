package com.vswitch.datainjection.device.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.VolumeReadingService;
import com.vswitch.datainjection.device.DeviceConfigRecord;
import com.vswitch.datainjection.device.DeviceStore;

@ExtendWith(MockitoExtension.class)
class DeviceMonthPrefixCacheTest {

    @Mock private VolumeReadingService volumeReadingService;
    @Mock private DeviceStore deviceStore;

    private DeviceMonthPrefixCache cache;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
        cache = new DeviceMonthPrefixCache(volumeReadingService, deviceStore, clock);
        when(deviceStore.findDeviceConfig("WM001"))
                .thenReturn(
                        Optional.of(
                                new DeviceConfigRecord(
                                        "WM001",
                                        "tenant-1",
                                        false,
                                        500,
                                        "[]",
                                        "UTC",
                                        100,
                                        100,
                                        "2026-06-15T10:00:00Z")));
    }

    @Test
    void cachesPrefixAndAddsLiveToday() {
        when(volumeReadingService.sumLitersThroughYesterday("WM001", "UTC")).thenReturn(12452.0);

        assertEquals(12472.0, cache.liveMonthLiters("WM001", 20.0), 0.001);
        assertEquals(12477.0, cache.liveMonthLiters("WM001", 25.0), 0.001);

        verify(volumeReadingService, times(1)).sumLitersThroughYesterday("WM001", "UTC");
    }

    @Test
    void reloadsWhenDayRolls() {
        when(volumeReadingService.sumLitersThroughYesterday(eq("WM001"), eq("UTC")))
                .thenReturn(100.0, 200.0);

        Clock dayOne =
                Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
        cache = new DeviceMonthPrefixCache(volumeReadingService, deviceStore, dayOne);
        assertEquals(120.0, cache.liveMonthLiters("WM001", 20.0), 0.001);

        Clock dayTwo =
                Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC);
        cache = new DeviceMonthPrefixCache(volumeReadingService, deviceStore, dayTwo);
        assertEquals(210.0, cache.liveMonthLiters("WM001", 10.0), 0.001);

        verify(volumeReadingService, times(2)).sumLitersThroughYesterday("WM001", "UTC");
    }

    @Test
    void evictForcesReload() {
        when(volumeReadingService.sumLitersThroughYesterday("WM001", "UTC"))
                .thenReturn(50.0, 75.0);

        assertEquals(60.0, cache.liveMonthLiters("WM001", 10.0), 0.001);
        cache.evict("WM001");
        assertEquals(85.0, cache.liveMonthLiters("WM001", 10.0), 0.001);

        verify(volumeReadingService, times(2)).sumLitersThroughYesterday("WM001", "UTC");
    }
}
