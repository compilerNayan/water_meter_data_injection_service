package com.vswitch.datainjection;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WaterReadingService {

    private final VolumeReadingService volumeReadingService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final com.vswitch.datainjection.device.DeviceFacade deviceFacade;

    WaterReadingService(
            VolumeReadingService volumeReadingService,
            TelemetryIngestionService telemetryIngestionService,
            com.vswitch.datainjection.device.DeviceFacade deviceFacade) {
        this.volumeReadingService = volumeReadingService;
        this.telemetryIngestionService = telemetryIngestionService;
        this.deviceFacade = deviceFacade;
    }

    CurrentReadingResponse getCurrentReading(String deviceId) {
        return deviceFacade.getCurrentReading(deviceId);
    }

    WaterUsageResponse getUsage(
            String deviceId, Instant from, Instant to, String granularity, String timezone) {
        return volumeReadingService.getUsage(deviceId, from, to, granularity, timezone);
    }

    DailySummaryResponse getDailySummary(
            String deviceId, String tenantId, LocalDate from, LocalDate to) {
        return volumeReadingService.getDailySummary(deviceId, from, to, "UTC");
    }

    DailySummaryResponse getDailySummary(
            String deviceId, String tenantId, LocalDate from, LocalDate to, String timezone) {
        return volumeReadingService.getDailySummary(deviceId, from, to, timezone);
    }

    HourlyPatternResponse getHourlyPattern(
            String deviceId, LocalDate from, LocalDate to, String timezone) {
        return volumeReadingService.getHourlyPattern(deviceId, from, to, timezone);
    }

    double getTodayUsedLiters(String deviceId, String tenantId) {
        return getTodayUsedLiters(deviceId, tenantId, "UTC");
    }

    double getTodayUsedLiters(String deviceId, String tenantId, String timezone) {
        return volumeReadingService.getTodayUsedLiters(deviceId, timezone);
    }

    double sumMonthLiters(String deviceId, String tenantId, String timezone) {
        return volumeReadingService.sumMonthLiters(deviceId, timezone);
    }

    MinutesTodayResponse getTodayMinutes(String deviceId, String timezone) {
        return volumeReadingService.getTodayMinutes(deviceId, timezone);
    }

    MinutesHistoryResponse getHistoryMinutes(String deviceId, int days, String timezone) {
        return volumeReadingService.getHistoryMinutes(deviceId, days, timezone);
    }

    ValveStateResponse getValveState(String deviceId, String tenantId) {
        return deviceFacade.getValveState(deviceId, tenantId);
    }

    ValveStateResponse updateValve(String deviceId, ValveUpdateRequest request) {
        String tenantId = requireTenantId(deviceId);
        return deviceFacade.setValveTarget(deviceId, tenantId, request);
    }

    private String requireTenantId(String deviceId) {
        return telemetryIngestionService
                .findDeviceState(deviceId)
                .map(DeviceStateRecord::tenantId)
                .filter(tenantId -> !tenantId.isBlank())
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Device state not found"));
    }
}
