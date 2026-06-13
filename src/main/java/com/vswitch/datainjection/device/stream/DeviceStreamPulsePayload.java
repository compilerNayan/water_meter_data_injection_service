package com.vswitch.datainjection.device.stream;

import java.time.Instant;

public record DeviceStreamPulsePayload(
        String tenantId,
        String deviceId,
        String serialNumber,
        Instant ts,
        double ml,
        double cumulativeLiters) {

    static DeviceStreamPulsePayload from(
            String tenantId,
            String serialNumber,
            String deviceId,
            Instant ts,
            double ml,
            double cumulativeLiters) {
        String resolvedDeviceId =
                firstNonBlank(deviceId, serialNumber, "").trim().toUpperCase();
        String resolvedSerial =
                firstNonBlank(serialNumber, resolvedDeviceId, resolvedDeviceId);
        return new DeviceStreamPulsePayload(
                tenantId.trim(),
                resolvedDeviceId,
                resolvedSerial,
                ts,
                ml,
                cumulativeLiters);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
