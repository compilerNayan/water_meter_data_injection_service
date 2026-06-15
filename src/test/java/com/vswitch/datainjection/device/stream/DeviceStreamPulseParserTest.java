package com.vswitch.datainjection.device.stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceStreamPulseParserTest {

    private final DeviceStreamPulseParser parser =
            new DeviceStreamPulseParser(new ObjectMapper());

    @Test
    void parsesStreamLine() {
        DeviceStreamPulsePayload payload =
                parser.parseLine(
                        "{\"tenantId\":\"63tk0y1\",\"serialNumber\":\"QJPDXN094\",\"ml\":45,"
                                + "\"cumulativeLiters\":123.4,\"ts\":\"2026-06-13T10:00:05Z\"}");

        assertEquals("63tk0y1", payload.tenantId());
        assertEquals("QJPDXN094", payload.deviceId());
        assertEquals("QJPDXN094", payload.serialNumber());
        assertEquals(45, payload.ml());
        assertEquals(123.4, payload.cumulativeLiters(), 0.001);
    }

    @Test
    void acceptsCurrentReadingAlias() {
        DeviceStreamPulsePayload payload =
                parser.parseLine(
                        "{\"tenantId\":\"63tk0y1\",\"deviceId\":\"WM000001\",\"ml\":10,"
                                + "\"currentReading\":88.8}");

        assertEquals("WM000001", payload.deviceId());
        assertEquals(88.8, payload.cumulativeLiters(), 0.001);
    }

    @Test
    void acceptsTodayUsageAliases() {
        DeviceStreamPulsePayload payload =
                parser.parseLine(
                        "{\"tenantId\":\"63tk0y1\",\"deviceId\":\"WM000001\",\"ml\":10,"
                                + "\"currentReading\":88.8,\"todayUsageLiters\":12.5}");

        assertEquals(88.8, payload.cumulativeLiters(), 0.001);
        assertEquals(12.5, payload.todayLiters(), 0.001);
    }

    @Test
    void rejectsMissingTenantId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parseLine("{\"serialNumber\":\"WM000001\",\"ml\":10}"));
    }
}
