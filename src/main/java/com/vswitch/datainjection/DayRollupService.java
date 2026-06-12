package com.vswitch.datainjection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceStore;
import com.vswitch.datainjection.device.TodaySlotRecord;

@Service
public class DayRollupService {

    private static final Logger log = LoggerFactory.getLogger(DayRollupService.class);

    private final UnitService unitService;
    private final DeviceStore deviceStore;
    private final long historyTtlSeconds;

    DayRollupService(
            UnitService unitService,
            DeviceStore deviceStore,
            @Value("${day.history.ttl.days:400}") int historyTtlDays) {
        this.unitService = unitService;
        this.deviceStore = deviceStore;
        this.historyTtlSeconds = Math.max(30L, historyTtlDays) * 24 * 3600;
    }

    void runRollup() {
        runRollup(ZoneOffset.UTC);
    }

    void runRollup(ZoneId zone) {
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        String localDate = DeviceStore.formatDate(yesterday);
        log.info("Running day rollup for {} in zone {}", localDate, zone);

        unitService.listAllUnits().stream()
                .filter(unit -> UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus()))
                .forEach(unit -> finalizeDay(unit, yesterday, zone, localDate));
    }

    void finalizeDay(UnitRecord unit, LocalDate date, ZoneId zone, String localDate) {
        List<TodaySlotRecord> slots =
                deviceStore.queryTodaySlotsByLocalDate(unit.deviceId(), localDate);
        if (slots.isEmpty()) {
            log.debug("No slots to roll up for device {} on {}", unit.deviceId(), localDate);
            return;
        }

        int[] milliliters = MinuteVolumeCsv.stitchDayFromSlots(slots, zone);
        double totalLiters = MinuteVolumeCsv.sumLiters(milliliters);
        long expiresAt = Instant.now().getEpochSecond() + historyTtlSeconds;

        deviceStore.putDayHistory(
                new DayHistoryRecord(
                        unit.deviceId(),
                        DayHistoryRecord.dayKeyFor(date),
                        unit.tenantId(),
                        MinuteVolumeCsv.encodeMl(milliliters),
                        totalLiters,
                        zone.getId(),
                        expiresAt));

        deviceStore.deleteTodaySlotsForLocalDate(unit.deviceId(), localDate);
        log.info(
                "Rolled up {} L for device {} on {}",
                totalLiters,
                unit.deviceId(),
                localDate);
    }
}
