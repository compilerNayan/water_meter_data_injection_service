package com.vswitch.datainjection.device.logs;

import java.time.Instant;

public record DeviceLogEntry(
        long seq,
        String tenantId,
        String deviceId,
        String serialNumber,
        String ts,
        String message,
        Instant receivedAt) {}
