package com.vswitch.datainjection.device.stream;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vswitch.datainjection.VolumeReadingService;
import com.vswitch.datainjection.device.DeviceConfigRecord;
import com.vswitch.datainjection.device.DeviceStore;

/**
 * In-memory cache of each device's month-to-date usage through yesterday (completed days only).
 * Refreshed lazily on day/month change and once daily so live ticks do not hit DynamoDB every
 * second.
 */
@Component
public class DeviceMonthPrefixCache {

    private static final Logger log = LoggerFactory.getLogger(DeviceMonthPrefixCache.class);

    private final VolumeReadingService volumeReadingService;
    private final DeviceStore deviceStore;
    private final Clock clock;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    DeviceMonthPrefixCache(
            VolumeReadingService volumeReadingService, DeviceStore deviceStore, Clock clock) {
        this.volumeReadingService = volumeReadingService;
        this.deviceStore = deviceStore;
        this.clock = clock;
    }

    /**
     * Month-to-date liters = cached usage through yesterday + live today from the pulse.
     */
    public Double liveMonthLiters(String deviceId, Double todayLiters) {
        String timezone = resolveTimezone(deviceId);
        double prefix = litersThroughYesterday(deviceId, timezone);
        if (todayLiters == null) {
            return prefix > 0 ? prefix : null;
        }
        return prefix + todayLiters;
    }

    double litersThroughYesterday(String deviceId, String timezone) {
        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        ZoneId zone = safeZone(timezone);
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        YearMonth month = YearMonth.from(today);
        LocalDate through = today.minusDays(1);

        CacheEntry cached = cache.get(key);
        if (cached != null
                && cached.month.equals(month)
                && cached.throughDate.equals(through)
                && cached.timezone.equals(timezone)) {
            return cached.liters;
        }

        double liters = volumeReadingService.sumLitersThroughYesterday(deviceId, timezone);
        cache.put(key, new CacheEntry(month, through, timezone, liters));
        return liters;
    }

    public void evict(String deviceId) {
        cache.remove(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId));
    }

    @Scheduled(cron = "${month.prefix.cache.refresh.cron:0 5 0 * * *}")
    void refreshDaily() {
        if (cache.isEmpty()) {
            return;
        }
        List<String> deviceIds = List.copyOf(cache.keySet());
        for (String deviceId : deviceIds) {
            CacheEntry previous = cache.remove(deviceId);
            if (previous != null) {
                litersThroughYesterday(deviceId, previous.timezone);
            }
        }
        log.info("Refreshed month-prefix usage cache for {} device(s)", deviceIds.size());
    }

    private String resolveTimezone(String deviceId) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .map(DeviceConfigRecord::timezone)
                .filter(timezone -> timezone != null && !timezone.isBlank())
                .orElse("UTC");
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneOffset.UTC;
        }
    }

    private record CacheEntry(
            YearMonth month, LocalDate throughDate, String timezone, double liters) {}
}
