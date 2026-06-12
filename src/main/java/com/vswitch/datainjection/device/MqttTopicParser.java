package com.vswitch.datainjection.device;

import java.util.Optional;

public final class MqttTopicParser {

    private MqttTopicParser() {}

    public record ParsedMqttTopic(String tenantId, String deviceType, String deviceId, String suffix) {}

    public static Optional<ParsedMqttTopic> parse(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }

        String[] parts = topic.split("/");
        if (parts.length < 4) {
            return Optional.empty();
        }

        String tenantId = parts[0];
        String deviceType = parts[1];
        String deviceId = parts[2];
        if (tenantId.isBlank() || deviceType.isBlank() || deviceId.isBlank()) {
            return Optional.empty();
        }
        if (!DeviceMqttTopics.DEVICE_TYPE.equals(deviceType)) {
            return Optional.empty();
        }

        String suffix = String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length));
        if (suffix.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ParsedMqttTopic(tenantId, deviceType, deviceId, suffix));
    }
}
