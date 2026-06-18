package com.vswitch.datainjection.device.stream;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.device.stream.command.DeviceStreamConnectionRegistry;
import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

@Service
public class DevicePresenceService {

    private static final Logger log = LoggerFactory.getLogger(DevicePresenceService.class);

    private final Map<String, PresenceEntry> byDevice = new ConcurrentHashMap<>();
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final DeviceStreamConnectionRegistry connectionRegistry;

    DevicePresenceService(
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster,
            DeviceStreamConnectionRegistry connectionRegistry) {
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
        this.connectionRegistry = connectionRegistry;
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
        boolean wasOnline = previous == null || previous.online();
        Instant lastSeen = previous != null ? previous.lastSeenAt() : at;
        byDevice.put(key, new PresenceEntry(tenantId, deviceId, lastSeen, false));
        if (wasOnline) {
            broadcastPresence(tenantId, deviceId, false, at);
        }
    }

    /** Socket connected — device is reachable regardless of water flow. */
    public void markOnline(String tenantId, String deviceId, Instant at) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry previous = byDevice.get(key);
        boolean wasOnline = previous != null && previous.online();
        byDevice.put(key, new PresenceEntry(tenantId, deviceId, at, true));

        if (!wasOnline) {
            broadcastPresence(tenantId, deviceId, true, at);
        }
    }

    /** Updates last activity time only; does not affect online/offline. */
    public void touchLastSeen(String tenantId, String deviceId, Instant receivedAt) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry previous = byDevice.get(key);
        if (previous == null) {
            return;
        }
        byDevice.put(
                key,
                new PresenceEntry(
                        previous.tenantId(),
                        previous.deviceId(),
                        receivedAt,
                        previous.online()));
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
            return entry.online();
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
                .map(PresenceEntry::lastSeenAt);
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
            String tenantId, String deviceId, Instant lastSeenAt, boolean online) {}
}
