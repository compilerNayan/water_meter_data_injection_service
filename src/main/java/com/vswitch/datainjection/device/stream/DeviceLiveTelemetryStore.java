package com.vswitch.datainjection.device.stream;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class DeviceLiveTelemetryStore {

    private final Map<String, DeviceLiveTelemetrySnapshot> latestByDevice =
            new ConcurrentHashMap<>();

    public void put(DeviceLiveTelemetrySnapshot snapshot) {
        latestByDevice.put(normalizeDeviceId(snapshot.deviceId()), snapshot);
    }

    public Optional<DeviceLiveTelemetrySnapshot> find(String deviceId) {
        return Optional.ofNullable(latestByDevice.get(normalizeDeviceId(deviceId)));
    }

    public void clear(String deviceId) {
        latestByDevice.remove(normalizeDeviceId(deviceId));
    }

    public int size() {
        return latestByDevice.size();
    }

    static String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return "";
        }
        return deviceId.trim().toUpperCase();
    }
}
