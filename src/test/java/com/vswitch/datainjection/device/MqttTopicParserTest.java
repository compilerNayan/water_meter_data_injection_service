package com.vswitch.datainjection.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTopicParserTest {

    @Test
    void parsesWater1sTopic() {
        var parsed =
                MqttTopicParser.parse("k3m9x2a/water_meter/WM000001/water/1s").orElseThrow();

        assertEquals("k3m9x2a", parsed.tenantId());
        assertEquals("water_meter", parsed.deviceType());
        assertEquals("WM000001", parsed.deviceId());
        assertEquals("water/1s", parsed.suffix());
    }

    @Test
    void parsesLifecycleEnrolledTopic() {
        var parsed =
                MqttTopicParser.parse("tenant1/water_meter/SERIAL123/lifecycle/enrolled")
                        .orElseThrow();

        assertEquals("lifecycle/enrolled", parsed.suffix());
    }

    @Test
    void parsesStatusTopic() {
        var parsed =
                MqttTopicParser.parse("k3m9x2a/water_meter/WM000001/status").orElseThrow();

        assertEquals("status", parsed.suffix());
    }

    @Test
    void rejectsLegacyAndInvalidTopics() {
        assertTrue(
                MqttTopicParser.parse("vswitch/water/k3m9x2a/WM000001/telemetry/second")
                        .isEmpty());
        assertTrue(MqttTopicParser.parse("k3m9x2a/other_type/WM000001/status").isEmpty());
        assertTrue(MqttTopicParser.parse("k3m9x2a/water_meter").isEmpty());
    }
}
