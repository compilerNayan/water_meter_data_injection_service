package com.vswitch.datainjection;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record UserRecord(
        String userId,
        String email,
        String phone,
        String firstName,
        String lastName,
        String displayName,
        String tenantId,
        boolean isTenantOwner,
        boolean onboardingComplete,
        String createdAt,
        String updatedAt) {

    static UserRecord fromItem(Map<String, AttributeValue> item) {
        return new UserRecord(
                stringValue(item, "userId"),
                stringValue(item, "email"),
                stringValue(item, "phone"),
                stringValue(item, "firstName"),
                stringValue(item, "lastName"),
                stringValue(item, "displayName"),
                stringValue(item, "tenantId"),
                boolValue(item, "isTenantOwner"),
                boolValue(item, "onboardingComplete"),
                stringValue(item, "createdAt"),
                stringValue(item, "updatedAt"));
    }

    Map<String, AttributeValue> toItem() {
        return Map.ofEntries(
                Map.entry("userId", AttributeValue.builder().s(userId).build()),
                Map.entry("email", AttributeValue.builder().s(email).build()),
                Map.entry("phone", AttributeValue.builder().s(phone).build()),
                Map.entry("firstName", AttributeValue.builder().s(firstName).build()),
                Map.entry("lastName", AttributeValue.builder().s(lastName).build()),
                Map.entry("displayName", AttributeValue.builder().s(displayName).build()),
                Map.entry("tenantId", AttributeValue.builder().s(tenantId).build()),
                Map.entry(
                        "isTenantOwner",
                        AttributeValue.builder().bool(isTenantOwner).build()),
                Map.entry(
                        "onboardingComplete",
                        AttributeValue.builder().bool(onboardingComplete).build()),
                Map.entry("createdAt", AttributeValue.builder().s(createdAt).build()),
                Map.entry("updatedAt", AttributeValue.builder().s(updatedAt).build()));
    }

    UserResponse toResponse() {
        return new UserResponse(
                userId,
                email,
                displayName,
                phone,
                firstName,
                lastName,
                tenantId,
                onboardingComplete,
                isTenantOwner);
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static boolean boolValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && Boolean.TRUE.equals(value.bool());
    }
}
