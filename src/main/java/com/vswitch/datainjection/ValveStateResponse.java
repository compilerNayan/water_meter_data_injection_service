package com.vswitch.datainjection;

public record ValveStateResponse(
        String deviceId,
        String timestamp,
        double targetPressurePercent,
        double actualPressurePercent,
        double lastUserPressurePercent,
        boolean isOff,
        String controlMode,
        Double quotaCapPercent,
        double effectivePressurePercent) {}
