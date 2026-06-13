package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

@Service
public class DeviceStreamIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamIngestionService.class);

    private final DeviceLiveTelemetryStore liveTelemetryStore;
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;

    DeviceStreamIngestionService(
            DeviceLiveTelemetryStore liveTelemetryStore,
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster) {
        this.liveTelemetryStore = liveTelemetryStore;
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
    }

    public void ingestPulse(DeviceStreamPulsePayload payload) {
        validate(payload);

        double flowRateLpm = payload.ml() / 1000.0 * 60.0;
        Instant receivedAt = Instant.now();
        DeviceLiveTelemetrySnapshot snapshot =
                new DeviceLiveTelemetrySnapshot(
                        payload.tenantId(),
                        payload.deviceId(),
                        payload.serialNumber(),
                        payload.ts(),
                        payload.ml(),
                        payload.cumulativeLiters(),
                        flowRateLpm,
                        receivedAt);
        liveTelemetryStore.put(snapshot);

        if (payload.ml() > 0) {
            broadcastWaterFlow(payload, flowRateLpm);
        }
    }

    public void clearLiveTelemetry(String deviceId) {
        liveTelemetryStore.clear(deviceId);
    }

    private void broadcastWaterFlow(DeviceStreamPulsePayload payload, double flowRateLpm) {
        try {
            liveUpdateBroadcaster.broadcast(
                    payload.tenantId(),
                    LiveUpdateMessage.waterFlow(
                            payload.tenantId(),
                            payload.deviceId(),
                            unitIdFor(payload.deviceId()),
                            payload.ts(),
                            payload.ml(),
                            flowRateLpm,
                            payload.cumulativeLiters()));
        } catch (Exception e) {
            log.warn(
                    "Failed to broadcast stream water_flow for {}/{}",
                    payload.tenantId(),
                    payload.deviceId(),
                    e);
        }
    }

    private static void validate(DeviceStreamPulsePayload payload) {
        if (payload.tenantId() == null || payload.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (payload.deviceId() == null || payload.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }
}
