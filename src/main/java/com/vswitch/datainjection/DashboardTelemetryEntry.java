package com.vswitch.datainjection;

public record DashboardTelemetryEntry(
        String unitId,
        String deviceId,
        double todayLiters,
        double monthLiters,
        boolean isOnline,
        String lastSeenAt,
        String status,
        double flowRateLpm,
        boolean quotaEnabled,
        double dailyLimitLiters,
        double quotaUsedLiters,
        Double quotaPercent,
        double valveOpenPercent,
        boolean valveIsOff,
        boolean hasAlert) {}
