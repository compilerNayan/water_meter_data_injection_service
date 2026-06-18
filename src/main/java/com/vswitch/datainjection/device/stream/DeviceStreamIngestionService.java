package com.vswitch.datainjection.device.stream;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.DeviceFacade;

@Service
public class DeviceStreamIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamIngestionService.class);

    private final DeviceLiveTelemetryStore liveTelemetryStore;
    private final DevicePresenceService presenceService;
    private final WaterFlowLiveBroadcastGate waterFlowBroadcastGate;
    private final DeviceFacade deviceFacade;

    DeviceStreamIngestionService(
            DeviceLiveTelemetryStore liveTelemetryStore,
            DevicePresenceService presenceService,
            WaterFlowLiveBroadcastGate waterFlowBroadcastGate,
            DeviceFacade deviceFacade) {
        this.liveTelemetryStore = liveTelemetryStore;
        this.presenceService = presenceService;
        this.waterFlowBroadcastGate = waterFlowBroadcastGate;
        this.deviceFacade = deviceFacade;
    }

    public void ingestPulse(DeviceStreamPulsePayload payload) {
        validate(payload);

        Instant receivedAt = Instant.now();
        presenceService.recordHeartbeat(payload.tenantId(), payload.deviceId(), receivedAt);

        if (payload.ml() <= 0) {
            return;
        }

        double flowRateLpm = payload.ml() / 1000.0 * 60.0;
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
        persistPulse(payload);
        waterFlowBroadcastGate.offer(payload, flowRateLpm);
    }

    private void persistPulse(DeviceStreamPulsePayload payload) {
        try {
            deviceFacade.ingestSecondPulse(
                    payload.tenantId(), payload.deviceId(), payload.ts(), payload.ml());
        } catch (Exception e) {
            log.debug(
                    "Failed to persist stream pulse for {}/{}",
                    payload.tenantId(),
                    payload.deviceId(),
                    e);
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
