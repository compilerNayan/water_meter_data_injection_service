package com.vswitch.datainjection;

public record CurrentReadingResponse(
        String deviceId,
        String timestamp,
        double flowRateLpm,
        double cumulativeLiters,
        String status) {}
