package com.vswitch.datainjection.device.stream;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DeviceStreamPulseParser {

    private final ObjectMapper objectMapper;

    public DeviceStreamPulseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeviceStreamPulsePayload parseLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty stream line");
        }

        Map<String, Object> json;
        try {
            json = objectMapper.readValue(line, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON line", e);
        }

        String tenantId = requiredString(json, "tenantId");
        String serialNumber = optionalString(json, "serialNumber", "serial", "deviceSerial");
        String deviceId = optionalString(json, "deviceId", "device_id");
        if (serialNumber == null && deviceId == null) {
            throw new IllegalArgumentException("serialNumber or deviceId is required");
        }

        double ml = requiredDouble(json, "ml");
        double cumulativeLiters =
                firstPresentDouble(json, "cumulativeLiters", "currentReading", "cumulative_liters");
        Double todayLiters =
                optionalDouble(json, "todayLiters", "todayUsageLiters", "todayUsage", "today_liters");

        String tsRaw = optionalString(json, "ts", "timestamp");
        Instant ts =
                tsRaw != null && !tsRaw.isBlank() ? Instant.parse(tsRaw) : Instant.now();

        return DeviceStreamPulsePayload.from(
                tenantId, serialNumber, deviceId, ts, ml, cumulativeLiters, todayLiters);
    }

    private static String requiredString(Map<String, Object> json, String key) {
        String value = optionalString(json, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value != null) {
                String text = value.toString().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static double requiredDouble(Map<String, Object> json, String key) {
        if (!json.containsKey(key) || json.get(key) == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return asDouble(json.get(key));
    }

    private static double firstPresentDouble(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return asDouble(json.get(key));
            }
        }
        return 0.0;
    }

    private static Double optionalDouble(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return asDouble(json.get(key));
            }
        }
        return null;
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
