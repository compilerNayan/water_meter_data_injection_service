package com.vswitch.datainjection;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DayHistoryRecord(
        String deviceId,
        String dayKey,
        String tenantId,
        String vCsv,
        double totalLiters,
        String timezone,
        long expiresAt) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String dayKeyFor(LocalDate date) {
        return "day#" + date.format(DATE_FORMAT);
    }

    public static LocalDate parseDate(String dayKey) {
        return LocalDate.parse(dayKey.substring("day#".length()), DATE_FORMAT);
    }

    public static DayHistoryRecord fromItem(Map<String, AttributeValue> item) {
        return new DayHistoryRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "dayKey"),
                stringValue(item, "tenantId"),
                stringValue(item, "v"),
                numberValue(item, "totalLiters"),
                stringValue(item, "timezone"),
                longValue(item, "expiresAt"));
    }

    public Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceId", AttributeValue.builder().s(deviceId).build(),
                "dayKey", AttributeValue.builder().s(dayKey).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "v", AttributeValue.builder().s(vCsv).build(),
                "totalLiters", AttributeValue.builder().n(Double.toString(totalLiters)).build(),
                "timezone", AttributeValue.builder().s(timezone).build(),
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
