package com.vswitch.datainjection;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record UnitRecord(
        String unitId,
        String tenantId,
        String deviceId,
        String name,
        String flatNumber,
        String floor,
        String block,
        String wing,
        String residentName,
        String phoneNumber,
        String notes,
        String enrollmentStatus,
        String unitInviteCode,
        String createdAt,
        String updatedAt) {

    static final String STATUS_PENDING = "pending";
    public static final String STATUS_ENROLLED = "enrolled";

    static UnitRecord fromItem(Map<String, AttributeValue> item) {
        return new UnitRecord(
                stringValue(item, "unitId"),
                stringValue(item, "tenantId"),
                stringValue(item, "deviceId"),
                stringValue(item, "name"),
                stringValue(item, "flatNumber"),
                stringValue(item, "floor"),
                stringValue(item, "block"),
                stringValue(item, "wing"),
                stringValue(item, "residentName"),
                stringValue(item, "phoneNumber"),
                stringValue(item, "notes"),
                stringValue(item, "enrollmentStatus"),
                stringValue(item, "unitInviteCode"),
                stringValue(item, "createdAt"),
                stringValue(item, "updatedAt"));
    }

    Map<String, AttributeValue> toItem() {
        return Map.ofEntries(
                Map.entry("unitId", AttributeValue.builder().s(unitId).build()),
                Map.entry("tenantId", AttributeValue.builder().s(tenantId).build()),
                Map.entry("deviceId", AttributeValue.builder().s(deviceId).build()),
                Map.entry("name", AttributeValue.builder().s(name).build()),
                Map.entry("flatNumber", AttributeValue.builder().s(flatNumber).build()),
                Map.entry("floor", AttributeValue.builder().s(floor).build()),
                Map.entry("block", AttributeValue.builder().s(block).build()),
                Map.entry("wing", AttributeValue.builder().s(wing).build()),
                Map.entry("residentName", AttributeValue.builder().s(residentName).build()),
                Map.entry("phoneNumber", AttributeValue.builder().s(phoneNumber).build()),
                Map.entry("notes", AttributeValue.builder().s(notes).build()),
                Map.entry(
                        "enrollmentStatus",
                        AttributeValue.builder().s(enrollmentStatus).build()),
                Map.entry(
                        "unitInviteCode",
                        AttributeValue.builder().s(unitInviteCode).build()),
                Map.entry("createdAt", AttributeValue.builder().s(createdAt).build()),
                Map.entry("updatedAt", AttributeValue.builder().s(updatedAt).build()));
    }

    UnitResponse toResponse() {
        return new UnitResponse(
                unitId,
                name,
                deviceId,
                flatNumber,
                floor,
                block,
                wing,
                residentName,
                phoneNumber,
                notes,
                enrollmentStatus,
                unitInviteCode);
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }
}
