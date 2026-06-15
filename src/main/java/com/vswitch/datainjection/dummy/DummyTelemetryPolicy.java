package com.vswitch.datainjection.dummy;

public final class DummyTelemetryPolicy {

    public static final int PULSES_PER_ACTIVE_MINUTE = 60;
    public static final int IDLE_SECONDS_AFTER_MINUTE = 10;
    public static final int SECONDS_PER_CYCLE =
            PULSES_PER_ACTIVE_MINUTE + IDLE_SECONDS_AFTER_MINUTE;
    public static final int MINUTES_PER_BUCKET = 30;
    public static final double DEFAULT_VALVE_TARGET_PERCENT = 100.0;

    /** Per-device offset so idle windows do not align across the fleet. */
    public static int initialCycleSecond(String tenantId, String serialNumber) {
        long seed = serialNumber.hashCode() ^ ((long) tenantId.hashCode() << 32);
        return Math.floorMod(seed, SECONDS_PER_CYCLE);
    }

    private DummyTelemetryPolicy() {}
}
