package com.vswitch.datainjection;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceStore;

/**
 * Read-only access to device state for tenant-wide queries.
 * All ingest and device writes go through {@link com.vswitch.datainjection.device.DeviceFacade}.
 */
@Service
public class TelemetryIngestionService {

    private final DeviceStore deviceStore;

    TelemetryIngestionService(DeviceStore deviceStore) {
        this.deviceStore = deviceStore;
    }

    Optional<DeviceStateRecord> findDeviceState(String deviceId) {
        return deviceStore.findDeviceState(deviceId);
    }
}
