package com.vswitch.datainjection.device.stream.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class DeviceStreamEnvelopeParserTest {

    private final DeviceStreamEnvelopeParser parser =
            new DeviceStreamEnvelopeParser(new ObjectMapper());

    @Test
    void parsesEnrollmentRequestEnvelope() {
        String line =
                """
                {"v":1,"category":"enrollment_request","tenantId":"","serialNumber":"WM001","data":{"serialNumber":"WM001","deviceType":"water_meter"}}
                """;

        DeviceStreamEnvelope envelope = parser.parseEnvelope(line.trim());

        assertEquals("enrollment_request", envelope.category());
        assertEquals("WM001", envelope.serialNumber());
        assertEquals("water_meter", envelope.data().get("deviceType").asText());
    }

    @Test
    void detectsEnvelopeLines() {
        assertTrue(parser.looksLikeEnvelope("{\"v\":1,\"category\":\"device_message\"}"));
        assertTrue(!parser.looksLikeEnvelope("{\"tenantId\":\"t\",\"ml\":1,\"serialNumber\":\"x\"}"));
    }
}
