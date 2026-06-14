package com.vswitch.datainjection.device.stream.command;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class DeviceStreamConnectionRegistry {

    private final ConcurrentHashMap<String, DeviceStreamSession> bySerial = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DeviceStreamSession, String> serialBySession =
            new ConcurrentHashMap<>();

    public void registerSession(DeviceStreamSession session) {
        serialBySession.put(session, null);
    }

    public void bindSerial(DeviceStreamSession session, String serialNumber) {
        if (session == null || serialNumber == null || serialNumber.isBlank()) {
            return;
        }
        String normalized = serialNumber.trim();
        String previous = serialBySession.put(session, normalized);
        if (previous != null && !previous.equals(normalized)) {
            bySerial.remove(previous, session);
        }
        bySerial.put(normalized, session);
    }

    public void unregisterSession(DeviceStreamSession session) {
        if (session == null) {
            return;
        }
        String serial = serialBySession.remove(session);
        if (serial != null) {
            bySerial.remove(serial, session);
        }
    }

    public Optional<DeviceStreamSession> findBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySerial.get(serialNumber.trim()));
    }

    public int activeSessionCount() {
        return bySerial.size();
    }
}
