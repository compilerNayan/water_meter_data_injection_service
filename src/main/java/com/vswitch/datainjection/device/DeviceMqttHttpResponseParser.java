package com.vswitch.datainjection.device;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DeviceMqttHttpResponseParser {

    private static final Pattern STATUS_LINE = Pattern.compile("HTTP/1\\.[01]\\s+(\\d+)");

    private DeviceMqttHttpResponseParser() {}

    static DeviceMqttHttpResponse parse(Map<String, Object> event, ObjectMapper objectMapper) {
        String raw = extractRawHttpText(event);
        if (raw != null && raw.contains("HTTP/")) {
            return parseHttpText(raw, objectMapper);
        }
        return new DeviceMqttHttpResponse(200, DeviceMqttHttpPayloadParser.parseJsonBody(event, objectMapper));
    }

    public static DeviceMqttHttpResponse parseHttpText(String raw, ObjectMapper objectMapper) {
        int statusCode = 200;
        Matcher matcher = STATUS_LINE.matcher(raw);
        if (matcher.find()) {
            statusCode = Integer.parseInt(matcher.group(1));
        }

        String jsonBody = extractHttpBody(raw);
        Map<String, Object> body = Map.of();
        if (jsonBody != null && !jsonBody.isBlank()) {
            try {
                body =
                        objectMapper.readValue(
                                jsonBody, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                body = Map.of("rawBody", jsonBody);
            }
        }
        return new DeviceMqttHttpResponse(statusCode, body);
    }

    private static String extractRawHttpText(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            return null;
        }
        for (String key :
                new String[] {"payload", "body", "message", "data", "payloadBase64", "mqttPayload"}) {
            Object value = event.get(key);
            if (value == null) {
                continue;
            }
            String text =
                    "payloadBase64".equals(key)
                            ? DeviceMqttHttpPayloadParser.decodeBase64(value.toString())
                            : value.toString();
            if (text.contains("HTTP/")) {
                return text;
            }
        }
        for (Object value : event.values()) {
            if (value != null && value.toString().contains("HTTP/")) {
                return value.toString();
            }
        }
        return null;
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
