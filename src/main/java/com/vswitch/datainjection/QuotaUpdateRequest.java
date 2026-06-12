package com.vswitch.datainjection;

import java.util.List;

public record QuotaUpdateRequest(
        boolean enabled, double dailyLimitLiters, List<QuotaStepDto> steps) {}
