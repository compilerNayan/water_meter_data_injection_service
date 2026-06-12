package com.vswitch.datainjection;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DevicePreEnrollRecord(
        String serialNumber,
        String tenantId,
        String status,
        String createdAt,
        String expiresAt,
        String createdByUserId,
        String enrolledAt) {

    static DevicePreEnrollRecord fromItem(Map<String, AttributeValue> item) {
        return new DevicePreEnrollRecord(
                stringValue(item, "serialNumber"),
                stringValue(item, "tenantId"),
                stringValue(item, "status"),
                stringValue(item, "createdAt"),
                stringValue(item, "expiresAt"),
                stringValue(item, "createdByUserId"),
                stringValue(item, "enrolledAt"));
    }

    Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("serialNumber", AttributeValue.builder().s(serialNumber).build());
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("expiresAt", AttributeValue.builder().s(expiresAt).build());
        item.put(
                "createdByUserId",
                AttributeValue.builder().s(createdByUserId).build());
        if (enrolledAt != null && !enrolledAt.isBlank()) {
            item.put("enrolledAt", AttributeValue.builder().s(enrolledAt).build());
        }
        return item;
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }
}
