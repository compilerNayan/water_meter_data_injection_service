package com.vswitch.datainjection.device.presence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.device.stream.DeviceLiveTelemetryStore;

@Service
public class PresenceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PresenceHistoryService.class);

    private final PresenceHistoryStore store;
    private final long ttlDays;

    public PresenceHistoryService(
            PresenceHistoryStore store,
            @Value("${presence.events.ttl.days:365}") long ttlDays) {
        this.store = store;
        this.ttlDays = ttlDays;
    }

    public void recordOnline(String tenantId, String deviceId, Instant at, String source) {
        persistTransition(tenantId, deviceId, at, PresenceTransitionRecord.STATUS_ONLINE, source);
    }

    public void recordOffline(String tenantId, String deviceId, Instant at, String source) {
        persistTransition(tenantId, deviceId, at, PresenceTransitionRecord.STATUS_OFFLINE, source);
    }

    private void persistTransition(
            String tenantId, String deviceId, Instant at, String status, String source) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()
                || at == null) {
            return;
        }

        try {
            String normalizedDeviceId = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
            long expiresAt = at.plus(ttlDays, ChronoUnit.DAYS).getEpochSecond();
            store.putEvent(
                    new PresenceTransitionRecord(
                            normalizedDeviceId,
                            PresenceTransitionRecord.formatEventAt(at),
                            tenantId,
                            status,
                            source,
                            expiresAt));
        } catch (Exception e) {
            log.warn(
                    "Failed to persist presence transition for {}/{} ({})",
                    tenantId,
                    deviceId,
                    status,
                    e);
        }
    }
}
