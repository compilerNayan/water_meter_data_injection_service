package com.vswitch.datainjection;

import java.util.List;

public record BuildingDailyResponse(String timezone, List<BuildingDailyEntry> days) {}
