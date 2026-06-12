package com.vswitch.datainjection.device;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceMqttHttpResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesHttpStatusAndJsonBody() {
        String raw =
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                        + "{\"targetPressurePercent\":80,\"actualPressurePercent\":78}";

        DeviceMqttHttpResponse response =
                DeviceMqttHttpResponseParser.parseHttpText(raw, objectMapper);

        assertEquals(200, response.statusCode());
        assertEquals(80, ((Number) response.body().get("targetPressurePercent")).intValue());
        assertEquals(78, ((Number) response.body().get("actualPressurePercent")).intValue());
    }

    @Test
    void parsesBadRequestStatus() {
        String raw = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\n\r\n{}";

        DeviceMqttHttpResponse response =
                DeviceMqttHttpResponseParser.parseHttpText(raw, objectMapper);

        assertEquals(400, response.statusCode());
    }
}
