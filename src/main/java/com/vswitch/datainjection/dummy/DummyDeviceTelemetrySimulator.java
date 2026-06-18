package com.vswitch.datainjection.dummy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import com.vswitch.datainjection.DummyDeviceRecord;
import com.vswitch.datainjection.DummyDeviceRepository;
import com.vswitch.datainjection.device.DeviceFacade;
import com.vswitch.datainjection.device.ThirtyMinuteBucketIngestionService;
import com.vswitch.datainjection.device.stream.DevicePresenceService;
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
    private final DevicePresenceService devicePresenceService;
    private final DeviceFacade deviceFacade;
    private final Clock clock;
    private final int minMl;
    private final int maxMl;
    private final long registryRefreshMillis;
    private final boolean offlineSimulationEnabled;
    private final DummyDeviceOfflineCoordinator offlineCoordinator;

    private final Map<String, DummyDeviceTelemetrySession> sessions = new ConcurrentHashMap<>();
    private volatile Instant lastRegistryRefresh = Instant.EPOCH;

    DummyDeviceTelemetrySimulator(
            DummyDeviceRepository dummyDeviceRepository,
            DeviceStreamIngestionService deviceStreamIngestionService,
            ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService,
            DevicePresenceService devicePresenceService,
            DeviceFacade deviceFacade,
            Clock clock,
            @Value("${dummy.telemetry.min.ml:10}") int minMl,
            @Value("${dummy.telemetry.max.ml:80}") int maxMl,
            @Value("${dummy.telemetry.registry.refresh.seconds:60}") long registryRefreshSeconds,
            @Value("${dummy.telemetry.offline.enabled:true}") boolean offlineSimulationEnabled,
            @Value("${dummy.telemetry.offline.min-fraction:0.03}") double offlineMinFraction,
            @Value("${dummy.telemetry.offline.max-fraction:0.06}") double offlineMaxFraction,
            @Value("${dummy.telemetry.offline.min-minutes:2}") long offlineMinMinutes,
            @Value("${dummy.telemetry.offline.max-minutes:5}") long offlineMaxMinutes,
            @Value("${dummy.telemetry.offline.reconcile.seconds:10}") long offlineReconcileSeconds,
            @Value("${dummy.telemetry.offline.cooldown.minutes:5}") long offlineCooldownMinutes) {
        this.dummyDeviceRepository = dummyDeviceRepository;
        this.deviceStreamIngestionService = deviceStreamIngestionService;
        this.thirtyMinuteBucketIngestionService = thirtyMinuteBucketIngestionService;
        this.devicePresenceService = devicePresenceService;
        this.deviceFacade = deviceFacade;
        this.clock = clock;
        this.minMl = minMl;
        this.maxMl = maxMl;
        this.registryRefreshMillis = Math.max(5, registryRefreshSeconds) * 1000L;
        this.offlineSimulationEnabled = offlineSimulationEnabled;
        this.offlineCoordinator =
                new DummyDeviceOfflineCoordinator(
                        offlineMinFraction,
                        offlineMaxFraction,
                        Duration.ofMinutes(Math.max(1, offlineMinMinutes)),
                        Duration.ofMinutes(Math.max(offlineMinMinutes, offlineMaxMinutes)),
                        Duration.ofSeconds(Math.max(5, offlineReconcileSeconds)),
                        Duration.ofMinutes(Math.max(0, offlineCooldownMinutes)));
    }

    @PostConstruct
    void warmRegistry() {
        refreshSessionsIfNeeded();
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    void tickAllDevices() {
        refreshSessionsIfNeeded();
        Instant now = clock.instant();
        if (offlineSimulationEnabled) {
            reconcileOfflineWindows(now);
        }
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

    public void registerDevice(String tenantId, String serialNumber) {
        if (tenantId == null
                || tenantId.isBlank()
                || serialNumber == null
                || serialNumber.isBlank()) {
            return;
        }
        String key = tenantId.trim() + "#" + serialNumber.trim().toUpperCase();
        Instant now = clock.instant();
        sessions.computeIfAbsent(
                key,
                ignored ->
                        new DummyDeviceTelemetrySession(
                                tenantId, serialNumber.trim().toUpperCase(), minMl, maxMl, now));
        log.info("Dummy telemetry session started for {}/{}", tenantId, serialNumber);
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
        List<DummyDeviceRecord> devices;
        try {
            devices = dummyDeviceRepository.listAll();
        } catch (Exception e) {
            log.warn("Dummy telemetry registry refresh failed: {}", e.toString());
            return;
        }
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

    private void reconcileOfflineWindows(Instant now) {
        List<DummyDeviceOfflineCoordinator.DeviceRef> devices = new ArrayList<>(sessions.size());
        for (DummyDeviceTelemetrySession session : sessions.values()) {
            devices.add(
                    new DummyDeviceOfflineCoordinator.DeviceRef(
                            session.tenantId(), session.serialNumber(), session.deviceKey()));
        }
        offlineCoordinator.reconcile(
                devices,
                now,
                new DummyDeviceOfflineCoordinator.Callbacks() {
                    @Override
                    public void onDeviceWentOffline(String tenantId, String deviceId, Instant at) {
                        devicePresenceService.markOffline(tenantId, deviceId, at);
                        try {
                            deviceFacade.markDeviceOffline(tenantId, deviceId);
                        } catch (Exception e) {
                            log.debug(
                                    "Failed to persist offline state for {}/{}",
                                    tenantId,
                                    deviceId,
                                    e);
                        }
                        log.debug("Dummy device offline {}/{} until simulated window ends", tenantId, deviceId);
                    }

                    @Override
                    public void onDeviceCameOnline(String tenantId, String deviceId, Instant at) {
                        devicePresenceService.markOnline(tenantId, deviceId, at);
                        log.debug("Dummy device back online {}/{}", tenantId, deviceId);
                    }
                });
    }

    private void emitTick(DummyDeviceTelemetrySession session, Instant now) {
        if (offlineSimulationEnabled
                && offlineCoordinator.isOffline(session.deviceKey(), now)) {
            return;
        }

        DummyDeviceTelemetrySession.TickResult result = session.tick(now);
        if (result.hasPulse()) {
            deviceStreamIngestionService.ingestPulse(
                    new DeviceStreamPulsePayload(
                            session.tenantId(),
                            session.serialNumber(),
                            session.serialNumber(),
                            result.pulseTimestamp(),
                            result.pulseMl(),
                            result.cumulativeLiters(),
                            result.todayLiters()));
        }
        if (result.hasBucket()) {
            thirtyMinuteBucketIngestionService.ingest(result.bucket());
        }
    }
}
