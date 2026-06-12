package com.vswitch.datainjection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

final class TenantMetadataHasher {

    private final ObjectMapper objectMapper;

    TenantMetadataHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    String computeHash(
            TenantRecord tenant, Optional<UserRecord> owner, List<UnitRecord> units) {
        try {
            String canonical = objectMapper.writeValueAsString(buildCanonical(tenant, owner, units));
            return sha256Hex(canonical);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metadata for hashing", e);
        }
    }

    private Map<String, Object> buildCanonical(
            TenantRecord tenant, Optional<UserRecord> owner, List<UnitRecord> units) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tenant", tenantCanonical(tenant));
        root.put("owner", ownerCanonical(owner));
        root.put("units", unitsCanonical(units));
        return root;
    }

    private Map<String, Object> tenantCanonical(TenantRecord tenant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tenant.name());
        map.put("structure", tenant.structure());
        map.put("tenantId", tenant.tenantId());
        map.put("updatedAt", tenant.updatedAt());
        return map;
    }

    private Map<String, Object> ownerCanonical(Optional<UserRecord> owner) {
        if (owner.isEmpty()) {
            return Map.of();
        }
        UserRecord user = owner.get();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("displayName", user.displayName());
        map.put("email", user.email());
        map.put("firstName", user.firstName());
        map.put("lastName", user.lastName());
        map.put("phone", user.phone());
        map.put("updatedAt", user.updatedAt());
        map.put("userId", user.userId());
        return map;
    }

    private List<Map<String, Object>> unitsCanonical(List<UnitRecord> units) {
        List<UnitRecord> sorted = new ArrayList<>(units);
        sorted.sort(Comparator.comparing(UnitRecord::unitId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnitRecord unit : sorted) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("block", unit.block());
            map.put("deviceId", unit.deviceId());
            map.put("enrollmentStatus", unit.enrollmentStatus());
            map.put("flatNumber", unit.flatNumber());
            map.put("floor", unit.floor());
            map.put("name", unit.name());
            map.put("notes", unit.notes());
            map.put("phoneNumber", unit.phoneNumber());
            map.put("residentName", unit.residentName());
            map.put("unitId", unit.unitId());
            map.put("unitInviteCode", unit.unitInviteCode());
            map.put("updatedAt", unit.updatedAt());
            map.put("wing", unit.wing());
            result.add(map);
        }
        return result;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
