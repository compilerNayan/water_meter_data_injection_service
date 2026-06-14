package com.vswitch.datainjection.device.stream.protocol;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DeviceStreamEnvelopeParser {

    private final ObjectMapper objectMapper;

    public DeviceStreamEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean looksLikeEnvelope(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.trim();
        return trimmed.contains("\"category\"")
                || trimmed.contains("\"v\"")
                || trimmed.contains("\"data\"");
    }

    public DeviceStreamEnvelope parseEnvelope(String line) {
        Map<String, Object> json = readJson(line);
        int version = asInt(json.get("v"), 1);
        String category = asString(json.get("category"));
        String tenantId = asString(json.get("tenantId"));
        String serialNumber = firstString(json, "serialNumber", "serial", "deviceSerial");
        JsonNode data = objectMapper.valueToTree(json.get("data"));
        return new DeviceStreamEnvelope(version, category, tenantId, serialNumber, data);
    }

    private Map<String, Object> readJson(String line) {
        try {
            return objectMapper.readValue(line, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON line", e);
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String firstString(Map<String, Object> json, String... keys) {
        for (String key : keys) {
            String value = asString(json.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
