package com.vswitch.datainjection.device.stream.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class DeviceStreamConnectionRegistryTest {

    @Test
    void registerSessionDoesNotThrowWhenSerialIsNotYetBound() {
        DeviceStreamConnectionRegistry registry = new DeviceStreamConnectionRegistry();
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        assertDoesNotThrow(() -> registry.registerSession(session));
        assertTrue(registry.findBySerial("WM001").isEmpty());
    }

    @Test
    void bindSerialMakesSessionDiscoverableBySerial() {
        DeviceStreamConnectionRegistry registry = new DeviceStreamConnectionRegistry();
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        registry.registerSession(session);
        registry.bindSerial(session, "WM001");

        assertEquals(session, registry.findBySerial("WM001").orElseThrow());
        assertEquals(1, registry.activeSessionCount());
    }

    @Test
    void unregisterSessionClearsBoundSerial() {
        DeviceStreamConnectionRegistry registry = new DeviceStreamConnectionRegistry();
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        registry.registerSession(session);
        registry.bindSerial(session, "WM001");
        registry.unregisterSession(session);

        assertTrue(registry.findBySerial("WM001").isEmpty());
        assertEquals(0, registry.activeSessionCount());
    }
}
