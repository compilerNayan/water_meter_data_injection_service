package com.vswitch.datainjection.live;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveUpdateMessage(
        String type,
        String tenantId,
        String deviceId,
        String unitId,
        String ts,
        Double ml,
        Double flowRateLpm,
        Double cumulativeLiters,
        String status,
        String periodStart,
        String action,
        String code,
        String message) {

    public static final String TYPE_WATER_FLOW = "water_flow";
    public static final String TYPE_BUCKET_30M = "bucket_30m";
    public static final String TYPE_DEVICE_PRESENCE = "device_presence";
    public static final String TYPE_SUBSCRIBED = "subscribed";
    public static final String TYPE_ERROR = "error";

    public static LiveUpdateMessage waterFlow(
            String tenantId,
            String deviceId,
            String unitId,
            Instant ts,
            double ml,
            double flowRateLpm,
            double cumulativeLiters) {
        return new LiveUpdateMessage(
                TYPE_WATER_FLOW,
                tenantId,
                deviceId,
                unitId,
                ts.toString(),
                ml,
                flowRateLpm,
                cumulativeLiters,
                "flowing",
                null,
                null,
                null,
                null);
    }

    public static LiveUpdateMessage bucket30m(
            String tenantId, String deviceId, String unitId, Instant periodStart) {
        return new LiveUpdateMessage(
                TYPE_BUCKET_30M,
                tenantId,
                deviceId,
                unitId,
                null,
                null,
                null,
                null,
                null,
                periodStart.toString(),
                "refresh",
                null,
                null);
    }

    public static LiveUpdateMessage devicePresence(
            String tenantId, String deviceId, String unitId, boolean online, Instant ts) {
        return new LiveUpdateMessage(
                TYPE_DEVICE_PRESENCE,
                tenantId,
                deviceId,
                unitId,
                ts.toString(),
                null,
                null,
                null,
                online ? "online" : "offline",
                null,
                null,
                null,
                null);
    }

    public static LiveUpdateMessage subscribed(String tenantId) {
        return new LiveUpdateMessage(
                TYPE_SUBSCRIBED,
                tenantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static LiveUpdateMessage error(String code, String message) {
        return new LiveUpdateMessage(
                TYPE_ERROR,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                code,
                message);
    }
}
