package com.vswitch.datainjection.device.stream;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.device.presence.PresenceHistoryService;
import com.vswitch.datainjection.device.presence.PresenceTransitionRecord;
import com.vswitch.datainjection.device.stream.command.DeviceStreamConnectionRegistry;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

@Service
public class DevicePresenceService {

    private static final Logger log = LoggerFactory.getLogger(DevicePresenceService.class);

    private final Map<String, PresenceEntry> byDevice = new ConcurrentHashMap<>();
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final DeviceStreamConnectionRegistry connectionRegistry;
    private final PresenceHistoryService presenceHistoryService;

    DevicePresenceService(
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster,
            DeviceStreamConnectionRegistry connectionRegistry,
            PresenceHistoryService presenceHistoryService) {
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
        this.connectionRegistry = connectionRegistry;
        this.presenceHistoryService = presenceHistoryService;
    }

    public void markOffline(String tenantId, String deviceId, Instant at) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry previous = byDevice.get(key);
        boolean wasOnline = previous != null && isEntryOnline(previous);
        Instant lastSeen = previous != null ? previous.lastHeartbeatAt() : at;
        byDevice.put(key, new PresenceEntry(tenantId, deviceId, lastSeen, false));
        if (wasOnline) {
            onPresenceTransition(
                    tenantId, deviceId, false, at, PresenceTransitionRecord.SOURCE_SOCKET);
        }
    }

    /** Socket connected — optimistic online until heartbeat timeout applies. */
    public void markOnline(String tenantId, String deviceId, Instant at) {
        recordHeartbeat(tenantId, deviceId, at);
    }

    /**
     * 1s device heartbeat (water_pulse with ml=0 allowed). Refreshes presence only — not persisted.
     */
    public void recordHeartbeat(String tenantId, String deviceId, Instant at) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry previous = byDevice.get(key);
        boolean wasOnline = previous != null && isEntryOnline(previous);
        byDevice.put(key, new PresenceEntry(tenantId, deviceId, at, true));

        if (!wasOnline) {
            onPresenceTransition(
                    tenantId, deviceId, true, at, PresenceTransitionRecord.SOURCE_HEARTBEAT);
        }
    }

    public void clear(String deviceId) {
        byDevice.remove(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId));
    }

    public boolean isOnline(String deviceId) {
        return resolveIsOnline(deviceId, Optional.empty());
    }

    public boolean resolveIsOnline(String deviceId, Optional<DeviceStateRecord> state) {
        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry entry = byDevice.get(key);
        if (entry != null) {
            return isEntryOnline(entry);
        }
        if (connectionRegistry.findBySerial(key).isPresent()) {
            return true;
        }
        return state
                .map(
                        deviceState ->
                                !DeviceStateRecord.STATUS_OFFLINE.equals(deviceState.status()))
                .orElse(false);
    }

    public Optional<Instant> lastSeenAt(String deviceId) {
        return Optional.ofNullable(byDevice.get(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId)))
                .map(PresenceEntry::lastHeartbeatAt);
    }

    @Scheduled(fixedRate = 2_000)
    void expireStaleHeartbeats() {
        Instant now = Instant.now();
        for (Map.Entry<String, PresenceEntry> entry : byDevice.entrySet()) {
            PresenceEntry current = entry.getValue();
            if (!current.online()) {
                continue;
            }
            if (!isStale(current.lastHeartbeatAt())) {
                continue;
            }
            byDevice.put(entry.getKey(), current.markOffline());
            onPresenceTransition(
                    current.tenantId(),
                    current.deviceId(),
                    false,
                    now,
                    PresenceTransitionRecord.SOURCE_TIMEOUT);
        }
    }

    private static boolean isEntryOnline(PresenceEntry entry) {
        return entry.online() && !isStale(entry.lastHeartbeatAt());
    }

    private static boolean isStale(Instant lastHeartbeatAt) {
        return Duration.between(lastHeartbeatAt, Instant.now())
                        .compareTo(DevicePresenceThreshold.OFFLINE_AFTER)
                > 0;
    }

    private void onPresenceTransition(
            String tenantId, String deviceId, boolean online, Instant ts, String source) {
        broadcastPresence(tenantId, deviceId, online, ts);
        if (online) {
            presenceHistoryService.recordOnline(tenantId, deviceId, ts, source);
        } else {
            presenceHistoryService.recordOffline(tenantId, deviceId, ts, source);
        }
    }

    private void broadcastPresence(
            String tenantId, String deviceId, boolean online, Instant ts) {
        try {
            liveUpdateBroadcaster.broadcast(
                    tenantId,
                    LiveUpdateMessage.devicePresence(
                            tenantId, deviceId, unitIdFor(deviceId), online, ts));
        } catch (Exception e) {
            log.warn("Failed to broadcast device_presence for {}/{}", tenantId, deviceId, e);
        }
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }

    private record PresenceEntry(
            String tenantId, String deviceId, Instant lastHeartbeatAt, boolean online) {

        PresenceEntry markOffline() {
            return new PresenceEntry(tenantId, deviceId, lastHeartbeatAt, false);
        }
    }
}
