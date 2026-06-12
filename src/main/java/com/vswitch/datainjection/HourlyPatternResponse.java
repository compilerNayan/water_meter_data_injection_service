package com.vswitch.datainjection;

import java.util.List;

public record HourlyPatternResponse(String unit, List<HourlyPatternHourResponse> hours) {}
