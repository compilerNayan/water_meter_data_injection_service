package com.vswitch.datainjection.device;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DeviceConfigRecord(
        String deviceId,
        String tenantId,
        boolean quotaEnabled,
        double dailyLimitLiters,
        String quotaStepsJson,
        String timezone,
        double valveTargetPercent,
        double lastUserPressurePercent,
        String updatedAt) {

    public static DeviceConfigRecord defaults(String deviceId, String tenantId, String now) {
        return defaults(deviceId, tenantId, now, 100, 100);
    }

    public static DeviceConfigRecord defaults(
            String deviceId,
            String tenantId,
            String now,
            double valveTargetPercent,
            double lastUserPressurePercent) {
        return new DeviceConfigRecord(
                deviceId,
                tenantId,
                false,
                500,
                "[]",
                "UTC",
                valveTargetPercent,
                lastUserPressurePercent,
                now);
    }

    public static DeviceConfigRecord fromItem(Map<String, AttributeValue> item) {
        return new DeviceConfigRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "tenantId"),
                boolValue(item, "quotaEnabled"),
                numberValue(item, "dailyLimitLiters", 500),
                stringValue(item, "quotaStepsJson", "[]"),
                stringValue(item, "timezone", "UTC"),
                numberValue(item, "valveTargetPercent", 100),
                numberValue(item, "lastUserPressurePercent", 100),
                stringValue(item, "updatedAt"));
    }

    public Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceId", AttributeValue.builder().s(deviceId).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "quotaEnabled", AttributeValue.builder().bool(quotaEnabled).build(),
                "dailyLimitLiters",
                        AttributeValue.builder().n(Double.toString(dailyLimitLiters)).build(),
                "quotaStepsJson", AttributeValue.builder().s(quotaStepsJson).build(),
                "timezone", AttributeValue.builder().s(timezone).build(),
                "valveTargetPercent",
                        AttributeValue.builder().n(Double.toString(valveTargetPercent)).build(),
                "lastUserPressurePercent",
                        AttributeValue.builder()
                                .n(Double.toString(lastUserPressurePercent))
                                .build(),
                "updatedAt", AttributeValue.builder().s(updatedAt).build());
    }

    DeviceQuotaConfig toQuotaConfig(java.util.List<com.vswitch.datainjection.QuotaStepDto> steps) {
        return new DeviceQuotaConfig(
                deviceId, tenantId, quotaEnabled, dailyLimitLiters, timezone, steps);
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        return stringValue(item, key, "");
    }

    private static String stringValue(
            Map<String, AttributeValue> item, String key, String defaultValue) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : defaultValue;
    }

    private static boolean boolValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.bool() != null && value.bool();
    }

    private static double numberValue(
            Map<String, AttributeValue> item, String key, double defaultValue) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value.n());
    }
}
