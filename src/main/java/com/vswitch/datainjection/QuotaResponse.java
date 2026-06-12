package com.vswitch.datainjection;

import java.util.List;

public record QuotaResponse(
        String deviceId,
        boolean enabled,
        double dailyLimitLiters,
        String timezone,
        List<QuotaStepDto> steps,
        QuotaStatusResponse status) {}
