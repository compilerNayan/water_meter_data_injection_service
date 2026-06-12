package com.vswitch.datainjection.device;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.CurrentReadingResponse;
import com.vswitch.datainjection.DayHistoryRecord;
import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.MinuteVolumeCsv;
import com.vswitch.datainjection.QuotaCalculator;
import com.vswitch.datainjection.QuotaStepDto;
import com.vswitch.datainjection.QuotaStepsJson;
import com.vswitch.datainjection.QuotaUpdateRequest;
import com.vswitch.datainjection.UnitRecord;
import com.vswitch.datainjection.ValveStateResponse;
import com.vswitch.datainjection.ValveUpdateRequest;
import com.vswitch.datainjection.VolumeReadingService;

@Service
@ConditionalOnProperty(name = "mock.telemetry.enabled", havingValue = "false", matchIfMissing = true)
public class DynamoDbDeviceFacade implements DeviceFacade {

    private static final Duration OFFLINE_THRESHOLD = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    protected final DeviceStore deviceStore;
    protected final VolumeReadingService volumeReadingService;
    protected final long slotTtlSeconds;

    public DynamoDbDeviceFacade(
            DeviceStore deviceStore,
            @Lazy VolumeReadingService volumeReadingService,
            @Value("${today.slots.ttl.hours:72}") int slotTtlHours) {
        this.deviceStore = deviceStore;
        this.volumeReadingService = volumeReadingService;
        this.slotTtlSeconds = Math.max(24, slotTtlHours) * 3600L;
    }

    @Override
    public DeviceQuotaConfig getQuotaConfig(String deviceId) {
        DeviceConfigRecord record = requireConfig(deviceId);
        return record.toQuotaConfig(QuotaStepsJson.fromJson(record.quotaStepsJson()));
    }

    @Override
    public DeviceQuotaConfig setQuota(
            String deviceId, String tenantId, QuotaUpdateRequest request) {
        validateQuotaRequest(request);
        DeviceConfigRecord existing =
                deviceStore
                        .findDeviceConfig(deviceId)
                        .orElseGet(
                                () ->
                                        DeviceConfigRecord.defaults(
                                                deviceId, tenantId, Instant.now().toString()));

        String now = Instant.now().toString();
        List<QuotaStepDto> steps = QuotaStepsJson.sortSteps(request.steps());
        DeviceConfigRecord updated =
                new DeviceConfigRecord(
                        deviceId,
                        tenantId,
                        request.enabled(),
                        request.dailyLimitLiters(),
                        QuotaStepsJson.toJson(steps),
                        existing.timezone(),
                        existing.valveTargetPercent(),
                        existing.lastUserPressurePercent(),
                        now);

        deviceStore.putDeviceConfig(updated);
        return updated.toQuotaConfig(steps);
    }

    @Override
    public void initializeDeviceConfig(String deviceId, String tenantId) {
        if (deviceStore.findDeviceConfig(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        deviceStore.putDeviceConfig(DeviceConfigRecord.defaults(deviceId, tenantId, now));
    }

    @Override
    public void initializeDeviceState(String deviceId, String tenantId) {
        if (deviceStore.findDeviceState(deviceId).isPresent()) {
            return;
        }
        String now = Instant.now().toString();
        DeviceStateRecord state =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        0,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        100,
                        100,
                        100,
                        now,
                        profileTag(deviceId),
                        now);
        deviceStore.putDeviceState(state);
    }

    @Override
    public void ingestSecondPulse(String tenantId, String deviceId, Instant ts, double ml) {
        DeviceStateRecord current = requireDeviceState(deviceId, tenantId);
        double flowLpm = ml / 1000.0 * 60;
        String now = Instant.now().toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        current.cumulativeLiters(),
                        flowLpm,
                        flowLpm > 0.2
                                ? DeviceStateRecord.STATUS_FLOWING
                                : DeviceStateRecord.STATUS_IDLE,
                        current.valveTargetPercent(),
                        current.valveActualPercent(),
                        current.lastUserPressurePercent(),
                        ts.toString(),
                        current.mockProfile(),
                        now);
        deviceStore.putDeviceState(updated);
    }

    @Override
    public void ingestLiveTick(
            UnitRecord unit,
            Instant minute,
            double volumeLiters,
            double avgFlowRateLpm,
            double valveTargetPercent,
            String status) {
        DeviceStateRecord current = requireDeviceState(unit.deviceId(), unit.tenantId());
        double actualPercent = computeActualPercent(unit.deviceId(), valveTargetPercent);
        String now = Instant.now().toString();

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        unit.deviceId(),
                        unit.tenantId(),
                        current.cumulativeLiters(),
                        avgFlowRateLpm,
                        status,
                        valveTargetPercent,
                        actualPercent,
                        resolveLastUserPressure(unit.deviceId(), current),
                        now,
                        current.mockProfile(),
                        now);

        deviceStore.putDeviceState(updated);
    }

    @Override
    public void ingest30MinuteBucket(ThirtyMinuteBucketPayload payload) {
        ZoneId zone = resolveTimezone(payload.deviceId());
        String localDate = payload.periodStart().atZone(zone).toLocalDate().format(DATE_FORMAT);

        int[] milliliters = new int[payload.minutes().size()];
        for (int i = 0; i < payload.minutes().size(); i++) {
            milliliters[i] = (int) Math.round(payload.minutes().get(i).ml());
        }

        long expiresAt = payload.periodStart().getEpochSecond() + slotTtlSeconds;
        deviceStore.putTodaySlot(
                new TodaySlotRecord(
                        payload.deviceId(),
                        TodaySlotRecord.slotKeyFor(payload.periodStart()),
                        payload.tenantId(),
                        localDate,
                        MinuteVolumeCsv.encodeMl(milliliters),
                        payload.cumulativeLiters(),
                        expiresAt));

        DeviceStateRecord current =
                requireDeviceState(payload.deviceId(), payload.tenantId());
        double actualPercent =
                computeActualPercent(payload.deviceId(), payload.valveTargetPercent());
        String now = Instant.now().toString();

        DeviceStateRecord updated =
                new DeviceStateRecord(
                        payload.deviceId(),
                        payload.tenantId(),
                        payload.cumulativeLiters(),
                        current.flowRateLpm(),
                        current.status(),
                        payload.valveTargetPercent(),
                        actualPercent,
                        resolveLastUserPressure(payload.deviceId(), current),
                        now,
                        current.mockProfile(),
                        now);

        deviceStore.putDeviceState(updated);
    }

    @Override
    public void ingestValveStateReport(String tenantId, String deviceId, double target, double actual) {
        DeviceStateRecord current = requireDeviceState(deviceId, tenantId);
        String now = Instant.now().toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        tenantId,
                        current.cumulativeLiters(),
                        current.flowRateLpm(),
                        current.status(),
                        target,
                        actual,
                        resolveLastUserPressure(deviceId, current),
                        current.lastSeenAt(),
                        current.mockProfile(),
                        now);
        deviceStore.putDeviceState(updated);
    }

    @Override
    public void writeDayHistory(DayHistoryRecord record) {
        deviceStore.putDayHistory(record);
    }

    @Override
    public boolean hasDayHistory(String deviceId, LocalDate date) {
        return deviceStore.findDayHistory(deviceId, date).isPresent();
    }

    @Override
    public void applyHistoricalCumulative(
            String deviceId, double additionalLiters, Instant lastHour) {
        deviceStore.applyHistoricalCumulative(deviceId, additionalLiters, lastHour);
    }

    @Override
    public CurrentReadingResponse getCurrentReading(String deviceId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        return new CurrentReadingResponse(
                deviceId,
                state.lastSeenAt(),
                state.flowRateLpm(),
                state.cumulativeLiters(),
                resolveStatus(state));
    }

    @Override
    public ValveStateResponse getValveState(String deviceId, String tenantId) {
        DeviceStateRecord state = requireDeviceState(deviceId);
        DeviceConfigRecord config = configOrDefaults(deviceId, tenantId, state);

        double target = config.valveTargetPercent();
        double actual = state.valveActualPercent();
        double lastUser = config.lastUserPressurePercent();
        boolean isOff = target <= 0;
        double effective = isOff ? 0 : actual;
        String controlMode = "manual";
        Double quotaCapPercent = null;

        try {
            DeviceQuotaConfig quota = getQuotaConfig(deviceId);
            if (quota.enabled()) {
                double used = volumeReadingService.getTodayUsedLiters(deviceId, quota.timezone());
                QuotaCalculator.QuotaCapResult cap =
                        QuotaCalculator.computeCap(
                                quota.steps(), used, quota.dailyLimitLiters());
                quotaCapPercent = cap.capPercent();
                if (quotaCapPercent != null) {
                    controlMode = "quota";
                    if (quotaCapPercent == 0) {
                        effective = 0;
                    } else if (!isOff) {
                        effective = Math.min(effective, quotaCapPercent);
                    }
                }
            }
        } catch (ResponseStatusException ignored) {
            // Quota config missing — valve without quota cap.
        }

        return new ValveStateResponse(
                deviceId,
                state.updatedAt(),
                target,
                actual,
                lastUser,
                isOff,
                controlMode,
                quotaCapPercent,
                effective);
    }

    @Override
    public ValveStateResponse setValveTarget(
            String deviceId, String tenantId, ValveUpdateRequest request) {
        DeviceConfigRecord config = configOrDefaults(deviceId, tenantId, null);
        double target;

        if (request.action() != null && "restore".equalsIgnoreCase(request.action())) {
            target = config.lastUserPressurePercent();
        } else if (request.pressurePercent() != null) {
            target = request.pressurePercent();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "pressurePercent or restore action required");
        }

        double clamped = Math.max(0, Math.min(100, target));
        double lastUser =
                clamped <= 0
                        ? (config.valveTargetPercent() > 0
                                ? config.valveTargetPercent()
                                : config.lastUserPressurePercent())
                        : clamped;

        String now = Instant.now().toString();
        DeviceConfigRecord updated =
                new DeviceConfigRecord(
                        deviceId,
                        tenantId,
                        config.quotaEnabled(),
                        config.dailyLimitLiters(),
                        config.quotaStepsJson(),
                        config.timezone(),
                        clamped,
                        lastUser,
                        now);

        deviceStore.putDeviceConfig(updated);
        return getValveState(deviceId, tenantId);
    }

    protected String profileTag(String deviceId) {
        return "";
    }

    protected double computeActualPercent(String deviceId, double target) {
        return target <= 0 ? 0 : target;
    }

    protected ZoneId resolveTimezone(String deviceId) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .map(DeviceConfigRecord::timezone)
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

    protected double resolveLastUserPressure(String deviceId, DeviceStateRecord state) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .map(DeviceConfigRecord::lastUserPressurePercent)
                .orElse(state.lastUserPressurePercent());
    }

    protected DeviceConfigRecord configOrDefaults(
            String deviceId, String tenantId, DeviceStateRecord state) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .orElseGet(
                        () -> {
                            double target =
                                    state != null ? state.valveTargetPercent() : 100;
                            double lastUser =
                                    state != null ? state.lastUserPressurePercent() : 100;
                            return DeviceConfigRecord.defaults(
                                    deviceId,
                                    tenantId,
                                    Instant.now().toString(),
                                    target,
                                    lastUser);
                        });
    }

    protected DeviceStateRecord requireDeviceState(String deviceId) {
        return deviceStore
                .findDeviceState(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device state not found"));
    }

    protected DeviceStateRecord requireDeviceState(String deviceId, String tenantId) {
        return deviceStore
                .findDeviceState(deviceId)
                .orElseGet(
                        () -> {
                            initializeDeviceState(deviceId, tenantId);
                            return deviceStore.findDeviceState(deviceId).orElseThrow();
                        });
    }

    protected DeviceConfigRecord requireConfig(String deviceId) {
        return deviceStore
                .findDeviceConfig(deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device config not found"));
    }

    protected static String resolveStatus(DeviceStateRecord state) {
        if (state.lastSeenAt() == null || state.lastSeenAt().isBlank()) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        Instant lastSeen = Instant.parse(state.lastSeenAt());
        if (Duration.between(lastSeen, Instant.now()).compareTo(OFFLINE_THRESHOLD) > 0) {
            return DeviceStateRecord.STATUS_OFFLINE;
        }
        return state.status();
    }

    protected static void validateQuotaRequest(QuotaUpdateRequest request) {
        if (request.dailyLimitLiters() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dailyLimitLiters must be positive");
        }
        for (QuotaStepDto step : request.steps()) {
            if (step.atLitersUsed() < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "step atLitersUsed must be non-negative");
            }
            if ("reduce_pressure".equals(step.action())
                    && (step.value() == null || step.value() <= 0)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "reduce_pressure step requires positive value");
            }
            if (!"reduce_pressure".equals(step.action()) && !"turn_off".equals(step.action())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "step action must be reduce_pressure or turn_off");
            }
        }
    }
}
