package com.vswitch.datainjection.device.stream.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceStreamHttpRequestBuilderTest {

    @Test
    void buildGetUsesCurlStyleHeaders() {
        String request = DeviceStreamHttpRequestBuilder.buildGet("/deviceenrollment/status");

        assertTrue(request.startsWith("GET /deviceenrollment/status HTTP/1.1\r\n"));
        assertTrue(request.contains("Host: localhost:8080\r\n"));
        assertTrue(request.contains("User-Agent: curl/8.0.1\r\n"));
        assertTrue(request.contains("Accept: */*\r\n"));
        assertTrue(request.endsWith("\r\n\r\n"));
    }

    @Test
    void buildPostIncludesJsonBodyAndContentLength() {
        String body = "{\"tenantId\":\"t1\",\"serialNumber\":\"WM001\"}";
        String request = DeviceStreamHttpRequestBuilder.buildPost("/deviceenrollment/notify", body);

        assertTrue(request.startsWith("POST /deviceenrollment/notify HTTP/1.1\r\n"));
        assertTrue(request.contains("Content-Type: application/json\r\n"));
        assertTrue(request.contains("Content-Length: " + body.getBytes().length + "\r\n"));
        assertTrue(request.endsWith("\r\n\r\n" + body));
    }
}
