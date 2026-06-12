package com.vswitch.datainjection;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record TenantRecord(
        String tenantId,
        String name,
        String ownerUserId,
        String structure,
        String createdAt,
        String updatedAt,
        String metadataHash,
        String adminInviteCode,
        String adminInviteExpiresAt) {

    static TenantRecord fromItem(Map<String, AttributeValue> item) {
        return new TenantRecord(
                stringValue(item, "tenantId"),
                stringValue(item, "name"),
                stringValue(item, "ownerUserId"),
                stringValue(item, "structure"),
                stringValue(item, "createdAt"),
                stringValue(item, "updatedAt"),
                optionalString(item, "metadataHash"),
                optionalString(item, "adminInviteCode"),
                optionalString(item, "adminInviteExpiresAt"));
    }

    Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("name", AttributeValue.builder().s(name).build());
        item.put("ownerUserId", AttributeValue.builder().s(ownerUserId).build());
        item.put("structure", AttributeValue.builder().s(structure).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        if (metadataHash != null && !metadataHash.isBlank()) {
            item.put("metadataHash", AttributeValue.builder().s(metadataHash).build());
        }
        if (adminInviteCode != null && !adminInviteCode.isBlank()) {
            item.put("adminInviteCode", AttributeValue.builder().s(adminInviteCode).build());
        }
        if (adminInviteExpiresAt != null && !adminInviteExpiresAt.isBlank()) {
            item.put(
                    "adminInviteExpiresAt",
                    AttributeValue.builder().s(adminInviteExpiresAt).build());
        }
        return item;
    }

    TenantRecord withMetadataHash(String hash) {
        return new TenantRecord(
                tenantId,
                name,
                ownerUserId,
                structure,
                createdAt,
                updatedAt,
                hash,
                adminInviteCode,
                adminInviteExpiresAt);
    }

    TenantRecord withAdminInvite(String code, String expiresAt) {
        return new TenantRecord(
                tenantId,
                name,
                ownerUserId,
                structure,
                createdAt,
                updatedAt,
                metadataHash,
                code,
                expiresAt);
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static String optionalString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.s() == null || value.s().isBlank()) {
            return null;
        }
        return value.s();
    }
}
