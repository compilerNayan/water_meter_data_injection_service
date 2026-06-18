package com.vswitch.datainjection.device.stream.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.vswitch.datainjection.device.stream.DeviceStreamPresenceCoordinator;

class DeviceStreamConnectionRegistryTest {

    @Test
    void registerSessionDoesNotThrowWhenSerialIsNotYetBound() {
        DeviceStreamConnectionRegistry registry =
                new DeviceStreamConnectionRegistry(mock(DeviceStreamPresenceCoordinator.class));
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        assertDoesNotThrow(() -> registry.registerSession(session));
        assertTrue(registry.findBySerial("WM001").isEmpty());
    }

    @Test
    void bindSerialMakesSessionDiscoverableBySerial() {
        DeviceStreamPresenceCoordinator coordinator = mock(DeviceStreamPresenceCoordinator.class);
        DeviceStreamConnectionRegistry registry = new DeviceStreamConnectionRegistry(coordinator);
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        registry.registerSession(session);
        registry.bindSerial(session, "WM001");

        assertEquals(session, registry.findBySerial("WM001").orElseThrow());
        assertEquals(1, registry.activeSessionCount());
        verify(coordinator).onSerialBound("WM001");
    }

    @Test
    void unregisterSessionClearsBoundSerial() {
        DeviceStreamPresenceCoordinator coordinator = mock(DeviceStreamPresenceCoordinator.class);
        DeviceStreamConnectionRegistry registry = new DeviceStreamConnectionRegistry(coordinator);
        DeviceStreamSession session = new DeviceStreamSession(new StringWriter());

        registry.registerSession(session);
        registry.bindSerial(session, "WM001");
        registry.unregisterSession(session);

        assertTrue(registry.findBySerial("WM001").isEmpty());
        assertEquals(0, registry.activeSessionCount());
        verify(coordinator).onSerialUnbound("WM001");
    }
}
