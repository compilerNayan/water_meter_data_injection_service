package com.vswitch.datainjection.device;

import java.util.List;

import com.vswitch.datainjection.QuotaStepDto;

public record DeviceQuotaConfig(
        String deviceId,
        String tenantId,
        boolean enabled,
        double dailyLimitLiters,
        String timezone,
        List<QuotaStepDto> steps) {}
