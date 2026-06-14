package com.vswitch.datainjection.device.stream.command;

import java.nio.charset.StandardCharsets;

/**
 * Builds raw HTTP/1.1 request strings the way curl sends them to a local device server.
 */
public final class DeviceStreamHttpRequestBuilder {

    private static final String HOST = "localhost:8080";
    private static final String USER_AGENT = "curl/8.0.1";

    private DeviceStreamHttpRequestBuilder() {}

    public static String buildGet(String path) {
        return buildRequest("GET", path, null, null);
    }

    public static String buildDelete(String path) {
        return buildRequest("DELETE", path, null, null);
    }

    public static String buildPost(String path, String jsonBody) {
        return buildRequest("POST", path, "application/json", jsonBody);
    }

    public static String buildPut(String path, String jsonBody) {
        return buildRequest("PUT", path, "application/json", jsonBody);
    }

    public static String buildPatch(String path, String jsonBody) {
        return buildRequest("PATCH", path, "application/json", jsonBody);
    }

    public static String buildRequest(
            String method, String path, String contentType, String body) {
        String normalizedPath = normalizePath(path);
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(normalizedPath).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(HOST).append("\r\n");
        request.append("User-Agent: ").append(USER_AGENT).append("\r\n");
        request.append("Accept: */*\r\n");

        if (contentType != null && body != null) {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            request.append("Content-Type: ").append(contentType).append("\r\n");
            request.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }

        request.append("Connection: close\r\n\r\n");
        if (body != null) {
            request.append(body);
        }
        return request.toString();
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
