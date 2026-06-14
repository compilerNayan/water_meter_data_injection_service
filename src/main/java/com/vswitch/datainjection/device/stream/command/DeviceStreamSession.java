package com.vswitch.datainjection.device.stream.command;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/** One open TCP connection from a device. */
public final class DeviceStreamSession {

    private final Writer writer;
    private final Object writeLock = new Object();
    private volatile String serialNumber;

    public DeviceStreamSession(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public void bindSerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return;
        }
        this.serialNumber = serialNumber.trim();
    }

    public String serialNumber() {
        return serialNumber;
    }

    public void sendLine(String line) throws IOException {
        synchronized (writeLock) {
            writer.write(line);
            writer.flush();
        }
    }
}
