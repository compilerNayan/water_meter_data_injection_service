package com.vswitch.datainjection.dummy;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vswitch.datainjection.DummyDeviceRecord;
import com.vswitch.datainjection.DummyDeviceRepository;
import com.vswitch.datainjection.device.ThirtyMinuteBucketIngestionService;
import com.vswitch.datainjection.device.stream.DeviceStreamIngestionService;
import com.vswitch.datainjection.device.stream.DeviceStreamPulsePayload;

/**
 * Generates 1 Hz water pulses and 30-minute buckets for devices registered in
 * {@link DummyDeviceRepository}.
 */
@Component
@ConditionalOnProperty(name = "dummy.telemetry.enabled", havingValue = "true", matchIfMissing = true)
public class DummyDeviceTelemetrySimulator {

    private static final Logger log = LoggerFactory.getLogger(DummyDeviceTelemetrySimulator.class);

    private final DummyDeviceRepository dummyDeviceRepository;
    private final DeviceStreamIngestionService deviceStreamIngestionService;
    private final ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService;
    private final Clock clock;
    private final int minMl;
    private final int maxMl;
    private final long registryRefreshMillis;

    private final Map<String, DummyDeviceTelemetrySession> sessions = new ConcurrentHashMap<>();
    private volatile Instant lastRegistryRefresh = Instant.EPOCH;

    DummyDeviceTelemetrySimulator(
            DummyDeviceRepository dummyDeviceRepository,
            DeviceStreamIngestionService deviceStreamIngestionService,
            ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService,
            Clock clock,
            @Value("${dummy.telemetry.min.ml:10}") int minMl,
            @Value("${dummy.telemetry.max.ml:80}") int maxMl,
            @Value("${dummy.telemetry.registry.refresh.seconds:60}") long registryRefreshSeconds) {
        this.dummyDeviceRepository = dummyDeviceRepository;
        this.deviceStreamIngestionService = deviceStreamIngestionService;
        this.thirtyMinuteBucketIngestionService = thirtyMinuteBucketIngestionService;
        this.clock = clock;
        this.minMl = minMl;
        this.maxMl = maxMl;
        this.registryRefreshMillis = Math.max(5, registryRefreshSeconds) * 1000L;
    }

    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    void tickAllDevices() {
        refreshSessionsIfNeeded();
        Instant now = clock.instant();
        for (DummyDeviceTelemetrySession session : sessions.values()) {
            try {
                emitTick(session, now);
            } catch (Exception e) {
                log.warn(
                        "Dummy telemetry tick failed for {}/{}",
                        session.tenantId(),
                        session.serialNumber(),
                        e);
            }
        }
    }

    public void evictTenant(String tenantId) {
        sessions.entrySet().removeIf(entry -> tenantId.equals(entry.getValue().tenantId()));
        lastRegistryRefresh = Instant.EPOCH;
        log.info("Evicted dummy telemetry sessions for tenant {}", tenantId);
    }

    void refreshSessionsIfNeeded() {
        Instant now = clock.instant();
        if (now.toEpochMilli() - lastRegistryRefresh.toEpochMilli() < registryRefreshMillis) {
            return;
        }
        lastRegistryRefresh = now;
        List<DummyDeviceRecord> devices = dummyDeviceRepository.listAll();
        Map<String, DummyDeviceTelemetrySession> next = new ConcurrentHashMap<>();
        for (DummyDeviceRecord device : devices) {
            String key = device.deviceKey();
            DummyDeviceTelemetrySession existing = sessions.get(key);
            next.put(
                    key,
                    existing != null
                            ? existing
                            : new DummyDeviceTelemetrySession(
                                    device.tenantId(),
                                    device.serialNumber(),
                                    minMl,
                                    maxMl,
                                    now));
        }
        sessions.clear();
        sessions.putAll(next);
        if (!devices.isEmpty()) {
            log.info("Dummy telemetry tracking {} device(s)", devices.size());
        }
    }

    private void emitTick(DummyDeviceTelemetrySession session, Instant now) {
        DummyDeviceTelemetrySession.TickResult result = session.tick(now);
        if (result.hasPulse()) {
            deviceStreamIngestionService.ingestPulse(
                    new DeviceStreamPulsePayload(
                            session.tenantId(),
                            session.serialNumber(),
                            session.serialNumber(),
                            result.pulseTimestamp(),
                            result.pulseMl(),
                            result.cumulativeLiters()));
        }
        if (result.hasBucket()) {
            thirtyMinuteBucketIngestionService.ingest(result.bucket());
        }
    }
}
