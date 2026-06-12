package com.vswitch.datainjection;

import java.util.List;

public record DailySummaryResponse(String unit, List<DailySummaryDayResponse> days) {}
