package com.vswitch.datainjection;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class V2WaterController {

    private final WaterReadingService waterReadingService;
    private final QuotaService quotaService;
    private final BuildingStatsService buildingStatsService;
    private final UnitService unitService;
    private final UserService userService;

    V2WaterController(
            WaterReadingService waterReadingService,
            QuotaService quotaService,
            BuildingStatsService buildingStatsService,
            UnitService unitService,
            UserService userService) {
        this.waterReadingService = waterReadingService;
        this.quotaService = quotaService;
        this.buildingStatsService = buildingStatsService;
        this.unitService = unitService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/valve")
    ValveStateResponse getValve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.getValveState(deviceId, tenantId);
    }

    @PutMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/valve")
    ValveStateResponse updateValve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestBody ValveUpdateRequest request) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.updateValve(deviceId, request);
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/current")
    CurrentReadingResponse getCurrent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.getCurrentReading(deviceId);
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/quota")
    QuotaResponse getQuota(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        requireDevice(tenantId, deviceId, jwt);
        return quotaService.getQuota(deviceId, tenantId);
    }

    @PutMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/quota")
    QuotaResponse updateQuota(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestBody QuotaUpdateRequest request) {
        requireDevice(tenantId, deviceId, jwt);
        return quotaService.updateQuota(deviceId, tenantId, request);
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/minutes/today")
    MinutesTodayResponse getTodayMinutes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "UTC") String timezone) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.getTodayMinutes(deviceId, timezone);
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/water/minutes/history")
    MinutesHistoryResponse getHistoryMinutes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "UTC") String timezone) {
        requireDevice(tenantId, deviceId, jwt);
        return waterReadingService.getHistoryMinutes(deviceId, days, timezone);
    }

    @GetMapping("/v2/tenants/{tenantId}/building/daily")
    BuildingDailyResponse getBuildingDaily(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "UTC") String timezone) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return buildingStatsService.getBuildingDaily(tenantId, days, timezone);
    }

    private void requireDevice(String tenantId, String deviceId, Jwt jwt) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        unitService
                .findByTenantAndDeviceId(tenantId, deviceId)
                .orElseThrow(
                        () ->
                                new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Unit not found"));
    }
}
