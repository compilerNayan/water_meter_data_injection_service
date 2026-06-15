package com.vswitch.datainjection;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DummyDeviceRecord(
        String deviceKey,
        String tenantId,
        String serialNumber,
        String createdAt,
        String createdByUserId) {

    static String deviceKeyFor(String tenantId, String serialNumber) {
        return tenantId.trim() + "#" + serialNumber.trim().toUpperCase();
    }

    static DummyDeviceRecord create(
            String tenantId, String serialNumber, String createdAt, String createdByUserId) {
        String normalizedSerial = serialNumber.trim().toUpperCase();
        return new DummyDeviceRecord(
                deviceKeyFor(tenantId, normalizedSerial),
                tenantId.trim(),
                normalizedSerial,
                createdAt,
                createdByUserId);
    }

    static DummyDeviceRecord fromItem(Map<String, AttributeValue> item) {
        return new DummyDeviceRecord(
                stringValue(item, "deviceKey"),
                stringValue(item, "tenantId"),
                stringValue(item, "serialNumber"),
                stringValue(item, "createdAt"),
                stringValue(item, "createdByUserId"));
    }

    Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceKey", AttributeValue.builder().s(deviceKey).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "serialNumber", AttributeValue.builder().s(serialNumber).build(),
                "createdAt", AttributeValue.builder().s(createdAt).build(),
                "createdByUserId", AttributeValue.builder().s(createdByUserId).build());
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }
}
