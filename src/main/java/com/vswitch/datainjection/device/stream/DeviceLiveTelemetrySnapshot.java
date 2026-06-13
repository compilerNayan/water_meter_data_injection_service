package com.vswitch.datainjection.device.stream;

import java.time.Instant;

public record DeviceLiveTelemetrySnapshot(
        String tenantId,
        String deviceId,
        String serialNumber,
        Instant ts,
        double ml,
        double cumulativeLiters,
        double flowRateLpm,
        Instant receivedAt) {}
