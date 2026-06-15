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

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;
import com.vswitch.datainjection.device.DeviceFacade;

@Service
public class DevicePresenceService {

    private static final Logger log = LoggerFactory.getLogger(DevicePresenceService.class);

    private final Map<String, PresenceEntry> byDevice = new ConcurrentHashMap<>();
    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final DeviceFacade deviceFacade;

    DevicePresenceService(
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster, DeviceFacade deviceFacade) {
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
        this.deviceFacade = deviceFacade;
    }

    public void recordPulse(String tenantId, String deviceId, Instant receivedAt) {
        if (tenantId == null
                || tenantId.isBlank()
                || deviceId == null
                || deviceId.isBlank()) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        PresenceEntry previous = byDevice.get(key);
        boolean wasOnline = previous != null && previous.online();
        byDevice.put(key, new PresenceEntry(tenantId, deviceId, receivedAt, true));

        try {
            deviceFacade.touchHeartbeat(tenantId, deviceId, receivedAt);
        } catch (Exception e) {
            log.debug("Failed to persist heartbeat for {}", deviceId, e);
        }

        if (!wasOnline) {
            broadcastPresence(tenantId, deviceId, true, receivedAt);
        }
    }

    public void clear(String deviceId) {
        byDevice.remove(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId));
    }

    public boolean isOnline(String deviceId) {
        PresenceEntry entry = byDevice.get(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId));
        if (entry == null) {
            return false;
        }
        return entry.online() && !isStale(entry.lastSeenAt());
    }

    public Optional<Instant> lastSeenAt(String deviceId) {
        return Optional.ofNullable(byDevice.get(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId)))
                .map(PresenceEntry::lastSeenAt);
    }

    @Scheduled(fixedRate = 5_000)
    void expireStaleDevices() {
        Instant now = Instant.now();
        for (Map.Entry<String, PresenceEntry> entry : byDevice.entrySet()) {
            PresenceEntry current = entry.getValue();
            if (!current.online()) {
                continue;
            }
            if (!isStale(current.lastSeenAt())) {
                continue;
            }
            byDevice.put(entry.getKey(), current.markOffline());
            broadcastPresence(current.tenantId(), current.deviceId(), false, now);
        }
    }

    private static boolean isStale(Instant lastSeenAt) {
        return Duration.between(lastSeenAt, Instant.now()).compareTo(DevicePresenceThreshold.OFFLINE_AFTER)
                > 0;
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
            String tenantId, String deviceId, Instant lastSeenAt, boolean online) {

        PresenceEntry markOffline() {
            return new PresenceEntry(tenantId, deviceId, lastSeenAt, false);
        }
    }
}
