package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class DeviceStreamIngestionService {

    private final DeviceLiveTelemetryStore liveTelemetryStore;
    private final DevicePresenceService presenceService;
    private final WaterFlowLiveBroadcastGate waterFlowBroadcastGate;

    DeviceStreamIngestionService(
            DeviceLiveTelemetryStore liveTelemetryStore,
            DevicePresenceService presenceService,
            WaterFlowLiveBroadcastGate waterFlowBroadcastGate) {
        this.liveTelemetryStore = liveTelemetryStore;
        this.presenceService = presenceService;
        this.waterFlowBroadcastGate = waterFlowBroadcastGate;
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
        presenceService.recordPulse(payload.tenantId(), payload.deviceId(), receivedAt);

        if (payload.ml() > 0) {
            waterFlowBroadcastGate.offer(payload, flowRateLpm);
        }
    }

    public void clearLiveTelemetry(String deviceId) {
        liveTelemetryStore.clear(deviceId);
        presenceService.clear(deviceId);
    }

    private static void validate(DeviceStreamPulsePayload payload) {
        if (payload.tenantId() == null || payload.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (payload.deviceId() == null || payload.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
    }
}
