package com.vswitch.datainjection.device.stream;

import java.time.Duration;

public final class DevicePresenceThreshold {

    public static final Duration OFFLINE_AFTER = Duration.ofSeconds(30);

    private DevicePresenceThreshold() {}
}
