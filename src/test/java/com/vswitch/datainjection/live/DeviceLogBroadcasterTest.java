package com.vswitch.datainjection.live;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.device.logs.DeviceLogEntry;

class DeviceLogBroadcasterTest {

    @Test
    void deviceLogMessageSerializesExpectedFields() throws Exception {
        DeviceLogEntry entry =
                new DeviceLogEntry(
                        5L,
                        "tenant-1",
                        "DEV001",
                        "DEV001",
                        "2026-06-17 10:00:00",
                        "hello",
                        Instant.parse("2026-06-17T10:00:01Z"));

        String json =
                new ObjectMapper()
                        .writeValueAsString(LiveUpdateMessage.deviceLog(entry));

        var tree = new ObjectMapper().readTree(json);
        assertEquals("device_log", tree.get("type").asText());
        assertEquals(5L, tree.get("seq").asLong());
        assertEquals("hello", tree.get("message").asText());
        assertEquals("DEV001", tree.get("deviceId").asText());
    }
}
