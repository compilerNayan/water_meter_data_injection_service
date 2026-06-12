package com.vswitch.datainjection.device;

public final class DeviceMqttTopics {

    public static final String DEVICE_TYPE = "water_meter";

    public static final String SUFFIX_STATUS = "status";
    public static final String SUFFIX_COMMAND = "command";
    public static final String SUFFIX_WATER_1S = "water/1s";
    public static final String SUFFIX_WATER_30M = "water/30m";
    public static final String SUFFIX_LIFECYCLE_ENROLLED = "lifecycle/enrolled";

    /** Subscribe filter for all water meter device topics. */
    public static final String ALL_WATER_METERS_SUBSCRIBE_FILTER = "+/water_meter/#";

    private DeviceMqttTopics() {}

    public static String prefix(String tenantId, String deviceId) {
        return tenantId.trim() + "/" + DEVICE_TYPE + "/" + deviceId.trim();
    }

    public static String topic(String tenantId, String deviceId, String suffix) {
        return prefix(tenantId, deviceId) + "/" + suffix;
    }

    public static String commandTopic(String tenantId, String deviceId) {
        return topic(tenantId, deviceId, SUFFIX_COMMAND);
    }

    public static String statusTopic(String tenantId, String deviceId) {
        return topic(tenantId, deviceId, SUFFIX_STATUS);
    }
}
