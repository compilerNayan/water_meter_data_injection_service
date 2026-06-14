package com.vswitch.datainjection.device.stream.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class DeviceStreamDownlinkMessageTest {

    @Test
    void encodesServerMessageEnvelope() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String line =
                DeviceStreamDownlinkMessage.toServerMessageLine(
                        mapper, "guid-123", "GET /status HTTP/1.1\r\nHost: localhost:8080\r\n\r\n");

        var json = mapper.readTree(line.trim());
        assertEquals(1, json.get("v").asInt());
        assertEquals("server_message", json.get("category").asText());
        assertEquals("guid-123", json.get("requestId").asText());
        assertTrue(json.get("payload").asText().contains("GET /status HTTP/1.1"));
    }
}
