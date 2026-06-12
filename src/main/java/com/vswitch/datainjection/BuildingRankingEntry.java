package com.vswitch.datainjection;

public record BuildingRankingEntry(
        String unitId,
        String name,
        double liters,
        Double quotaPercent,
        String block,
        String wing) {}
