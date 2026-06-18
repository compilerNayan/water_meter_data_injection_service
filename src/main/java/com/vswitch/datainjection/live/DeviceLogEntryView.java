package com.vswitch.datainjection.live;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vswitch.datainjection.device.logs.DeviceLogEntry;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceLogEntryView(
        long seq, String ts, String message, String receivedAt, String serialNumber) {

    public static DeviceLogEntryView from(DeviceLogEntry entry) {
        return new DeviceLogEntryView(
                entry.seq(),
                entry.ts(),
                entry.message(),
                entry.receivedAt().toString(),
                entry.serialNumber());
    }
}
