package com.vswitch.datainjection;

import java.util.List;

public record WaterUsageResponse(
        String deviceId,
        String from,
        String to,
        String granularity,
        String unit,
        List<UsageDataPointResponse> dataPoints,
        UsageSummaryResponse summary) {}
