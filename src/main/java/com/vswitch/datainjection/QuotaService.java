package com.vswitch.datainjection;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceFacade;
import com.vswitch.datainjection.device.DeviceQuotaConfig;

@Service
public class QuotaService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeviceFacade deviceFacade;
    private final WaterReadingService waterReadingService;

    QuotaService(DeviceFacade deviceFacade, WaterReadingService waterReadingService) {
        this.deviceFacade = deviceFacade;
        this.waterReadingService = waterReadingService;
    }

    QuotaResponse getQuota(String deviceId, String tenantId) {
        DeviceQuotaConfig config = loadQuotaConfig(deviceId, tenantId);
        return buildResponse(deviceId, tenantId, config);
    }

    private DeviceQuotaConfig loadQuotaConfig(String deviceId, String tenantId) {
        try {
            return deviceFacade.getQuotaConfig(deviceId);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            if (e.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
                deviceFacade.initializeDeviceConfig(deviceId, tenantId);
                return deviceFacade.getQuotaConfig(deviceId);
            }
            throw e;
        }
    }

    QuotaResponse updateQuota(String deviceId, String tenantId, QuotaUpdateRequest request) {
        DeviceQuotaConfig config = deviceFacade.setQuota(deviceId, tenantId, request);
        return buildResponse(deviceId, tenantId, config);
    }

    private QuotaResponse buildResponse(
            String deviceId, String tenantId, DeviceQuotaConfig config) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        double usedLiters =
                waterReadingService.getTodayUsedLiters(deviceId, tenantId, config.timezone());
        QuotaCalculator.QuotaCapResult cap =
                QuotaCalculator.computeCap(
                        config.steps(), usedLiters, config.dailyLimitLiters());

        Double quotaCapPercent = cap.capPercent();
        if (!config.enabled()) {
            quotaCapPercent = null;
        }

        QuotaStatusResponse status =
                new QuotaStatusResponse(
                        today.format(DATE_FORMAT),
                        usedLiters,
                        config.enabled() ? cap.activeStepIndex() : -1,
                        config.enabled() ? quotaCapPercent : null,
                        cap.remainingLiters(),
                        config.enabled() ? cap.nextStepAtLiters() : null);

        return new QuotaResponse(
                deviceId,
                config.enabled(),
                config.dailyLimitLiters(),
                config.timezone(),
                config.steps(),
                status);
    }
}
