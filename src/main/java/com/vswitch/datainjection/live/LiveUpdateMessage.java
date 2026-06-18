package com.vswitch.datainjection.live;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vswitch.datainjection.device.logs.DeviceLogEntry;

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
        String message,
        Double todayLiters,
        List<WaterFlowTickDevice> devices,
        Long seq,
        String serialNumber,
        String receivedAt,
        Long fromSeq,
        Long toSeq,
        Long nextSeq,
        String reason,
        List<DeviceLogEntryView> entries) {

    public static final String TYPE_WATER_FLOW = "water_flow";
    public static final String TYPE_WATER_FLOW_TICK = "water_flow_tick";
    public static final String TYPE_BUCKET_30M = "bucket_30m";
    public static final String TYPE_DEVICE_PRESENCE = "device_presence";
    public static final String TYPE_SUBSCRIBED = "subscribed";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DEVICE_LOG = "device_log";
    public static final String TYPE_DEVICE_LOG_BATCH = "device_log_batch";
    public static final String TYPE_DEVICE_LOG_RESET = "device_log_reset";

    public static LiveUpdateMessage waterFlow(
            String tenantId,
            String deviceId,
            String unitId,
            Instant ts,
            double ml,
            double flowRateLpm,
            double cumulativeLiters,
            Double todayLiters) {
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
                null,
                todayLiters,
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

    public static LiveUpdateMessage waterFlow(
            String tenantId,
            String deviceId,
            String unitId,
            Instant ts,
            double ml,
            double flowRateLpm,
            double cumulativeLiters) {
        return waterFlow(
                tenantId, deviceId, unitId, ts, ml, flowRateLpm, cumulativeLiters, null);
    }

    public static LiveUpdateMessage waterFlowTick(
            String tenantId, Instant tickTs, List<WaterFlowTickDevice> devices) {
        return new LiveUpdateMessage(
                TYPE_WATER_FLOW_TICK,
                tenantId,
                null,
                null,
                tickTs.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                devices,
                null,
                null,
                null,
                null,
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
                message,
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

    public static LiveUpdateMessage deviceLog(DeviceLogEntry entry) {
        return new LiveUpdateMessage(
                TYPE_DEVICE_LOG,
                entry.tenantId(),
                entry.deviceId(),
                null,
                entry.ts(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                entry.message(),
                null,
                null,
                entry.seq(),
                entry.serialNumber(),
                entry.receivedAt().toString(),
                null,
                null,
                null,
                null,
                null);
    }

    public static LiveUpdateMessage deviceLogBatch(
            String tenantId, String deviceId, List<DeviceLogEntry> entries) {
        if (entries.isEmpty()) {
            return new LiveUpdateMessage(
                    TYPE_DEVICE_LOG_BATCH,
                    tenantId,
                    deviceId,
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
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of());
        }
        long fromSeq = entries.get(0).seq();
        long toSeq = entries.get(entries.size() - 1).seq();
        List<DeviceLogEntryView> views = entries.stream().map(DeviceLogEntryView::from).toList();
        return new LiveUpdateMessage(
                TYPE_DEVICE_LOG_BATCH,
                tenantId,
                deviceId,
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
                null,
                null,
                null,
                null,
                null,
                fromSeq,
                toSeq,
                null,
                null,
                views);
    }

    public static LiveUpdateMessage deviceLogReset(String tenantId, String deviceId) {
        return new LiveUpdateMessage(
                TYPE_DEVICE_LOG_RESET,
                tenantId,
                deviceId,
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                "size_limit",
                null);
    }
}
