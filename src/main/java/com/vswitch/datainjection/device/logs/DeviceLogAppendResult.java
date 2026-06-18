package com.vswitch.datainjection.device.logs;

import java.util.List;

public record DeviceLogAppendResult(boolean fileReset, List<DeviceLogEntry> entries) {

    public static DeviceLogAppendResult empty() {
        return new DeviceLogAppendResult(false, List.of());
    }
}
