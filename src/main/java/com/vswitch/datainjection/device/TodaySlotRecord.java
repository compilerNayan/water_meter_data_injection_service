package com.vswitch.datainjection.device;

import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record TodaySlotRecord(
        String deviceId,
        String slotKey,
        String tenantId,
        String localDate,
        String vCsv,
        double cumulativeLiters,
        long expiresAt) {

    public static String slotKeyFor(Instant periodStart) {
        return "slot#" + periodStart.toString();
    }

    public static Instant parsePeriodStart(String slotKey) {
        return Instant.parse(slotKey.substring("slot#".length()));
    }

    static TodaySlotRecord fromItem(Map<String, AttributeValue> item) {
        return new TodaySlotRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "slotKey"),
                stringValue(item, "tenantId"),
                stringValue(item, "localDate"),
                stringValue(item, "v"),
                numberValue(item, "cumulativeLiters"),
                longValue(item, "expiresAt"));
    }

    public Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceId", AttributeValue.builder().s(deviceId).build(),
                "slotKey", AttributeValue.builder().s(slotKey).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "localDate", AttributeValue.builder().s(localDate).build(),
                "v", AttributeValue.builder().s(vCsv).build(),
                "cumulativeLiters",
                        AttributeValue.builder().n(Double.toString(cumulativeLiters)).build(),
                "expiresAt", AttributeValue.builder().n(Long.toString(expiresAt)).build());
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static double numberValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return 0;
        }
        return Double.parseDouble(value.n());
    }

    private static long longValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return 0;
        }
        return Long.parseLong(value.n());
    }
}
