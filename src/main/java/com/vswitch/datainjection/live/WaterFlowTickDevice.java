package com.vswitch.datainjection.live;

public record WaterFlowTickDevice(
        String deviceId,
        String unitId,
        String ts,
        double ml,
        double flowRateLpm,
        double cumulativeLiters,
        String status) {}
