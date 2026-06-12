package com.vswitch.datainjection.device;

import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final class DeviceMqttHttpPayloadParser {

    private DeviceMqttHttpPayloadParser() {}

    static Map<String, Object> parseJsonBody(Map<String, Object> event, ObjectMapper objectMapper) {
        if (event == null || event.isEmpty()) {
            return Map.of();
        }

        for (String key : new String[] {"targetPressurePercent", "actualPressurePercent", "valveTargetPercent"}) {
            if (event.containsKey(key)) {
                return event;
            }
        }

        for (String key :
                new String[] {"payload", "body", "message", "data", "payloadBase64", "mqttPayload"}) {
            Object value = event.get(key);
            if (value == null) {
                continue;
            }
            String text = decodeIfNeeded(key, value.toString());
            String jsonBody = extractHttpBody(text);
            if (jsonBody == null || jsonBody.isBlank()) {
                continue;
            }
            try {
                return objectMapper.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                // try next field
            }
        }

        for (Object value : event.values()) {
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (!text.contains("HTTP/")) {
                continue;
            }
            String jsonBody = extractHttpBody(text);
            if (jsonBody == null || jsonBody.isBlank()) {
                continue;
            }
            try {
                return objectMapper.readValue(jsonBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                // try next value
            }
        }

        return event;
    }

    private static String decodeIfNeeded(String key, String value) {
        if (!"payloadBase64".equals(key)) {
            return value;
        }
        return decodeBase64(value);
    }

    static String decodeBase64(String value) {
        try {
            return new String(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String extractHttpBody(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf("\r\n\r\n");
        if (separator >= 0) {
            return raw.substring(separator + 4).trim();
        }
        separator = raw.indexOf("\n\n");
        if (separator >= 0) {
            return raw.substring(separator + 2).trim();
        }
        return raw.trim();
    }
}
