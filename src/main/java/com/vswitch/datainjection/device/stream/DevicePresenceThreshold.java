package com.vswitch.datainjection.device.stream;

import java.time.Duration;

public final class DevicePresenceThreshold {

    /** No 1s socket heartbeat for this long → device is offline. */
    public static final Duration OFFLINE_AFTER = Duration.ofSeconds(5);

    private DevicePresenceThreshold() {}
}
