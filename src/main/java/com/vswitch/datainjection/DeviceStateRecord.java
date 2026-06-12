package com.vswitch.datainjection;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DeviceStateRecord(
        String deviceId,
        String tenantId,
        double cumulativeLiters,
        double flowRateLpm,
        String status,
        double valveTargetPercent,
        double valveActualPercent,
        double lastUserPressurePercent,
        String lastSeenAt,
        String mockProfile,
        String updatedAt) {

    public static final String STATUS_FLOWING = "flowing";
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_OFFLINE = "offline";
    public static final String STATUS_LEAK_SUSPECTED = "leak_suspected";

    public static DeviceStateRecord fromItem(Map<String, AttributeValue> item) {
        return new DeviceStateRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "tenantId"),
                numberValue(item, "cumulativeLiters"),
                numberValue(item, "flowRateLpm"),
                stringValue(item, "status"),
                numberValue(item, "valveTargetPercent", 100),
                numberValue(item, "valveActualPercent", 100),
                numberValue(item, "lastUserPressurePercent", 100),
                stringValue(item, "lastSeenAt"),
                stringValue(item, "mockProfile"),
                stringValue(item, "updatedAt"));
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("deviceId", AttributeValue.builder().s(deviceId).build());
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put(
                "cumulativeLiters",
                AttributeValue.builder().n(Double.toString(cumulativeLiters)).build());
        item.put("flowRateLpm", AttributeValue.builder().n(Double.toString(flowRateLpm)).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put(
                "valveTargetPercent",
                AttributeValue.builder().n(Double.toString(valveTargetPercent)).build());
        item.put(
                "valveActualPercent",
                AttributeValue.builder().n(Double.toString(valveActualPercent)).build());
        item.put(
                "lastUserPressurePercent",
                AttributeValue.builder().n(Double.toString(lastUserPressurePercent)).build());
        item.put("lastSeenAt", AttributeValue.builder().s(lastSeenAt).build());
        if (mockProfile != null && !mockProfile.isBlank()) {
            item.put("mockProfile", AttributeValue.builder().s(mockProfile).build());
        }
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        return item;
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static double numberValue(Map<String, AttributeValue> item, String key) {
        return numberValue(item, key, 0);
    }

    private static double numberValue(Map<String, AttributeValue> item, String key, double defaultValue) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value.n());
    }
}
