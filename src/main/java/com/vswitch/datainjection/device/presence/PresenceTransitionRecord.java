package com.vswitch.datainjection.device.presence;

import java.time.Instant;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record PresenceTransitionRecord(
        String deviceId,
        String eventAt,
        String tenantId,
        String status,
        String source,
        long expiresAt) {

    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";

    public static final String SOURCE_HEARTBEAT = "heartbeat";
    public static final String SOURCE_SOCKET = "socket";
    public static final String SOURCE_TIMEOUT = "timeout";

    public Instant eventInstant() {
        return Instant.parse(eventAt);
    }

    public boolean isOnline() {
        return STATUS_ONLINE.equals(status);
    }

    public static String formatEventAt(Instant instant) {
        return instant.toString();
    }

    public static PresenceTransitionRecord fromItem(Map<String, AttributeValue> item) {
        return new PresenceTransitionRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "eventAt"),
                stringValue(item, "tenantId"),
                stringValue(item, "status"),
                stringValue(item, "source"),
                longValue(item, "expiresAt"));
    }

    public Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceId", AttributeValue.builder().s(deviceId).build(),
                "eventAt", AttributeValue.builder().s(eventAt).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "status", AttributeValue.builder().s(status).build(),
                "source", AttributeValue.builder().s(source).build(),
                "expiresAt", AttributeValue.builder().n(Long.toString(expiresAt)).build());
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static long longValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return 0;
        }
        return Long.parseLong(value.n());
    }
}
