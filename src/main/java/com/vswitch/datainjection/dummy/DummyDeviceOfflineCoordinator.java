package com.vswitch.datainjection.dummy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps roughly 3–6% of dummy devices in a simulated offline window (2–5 minutes each).
 */
final class DummyDeviceOfflineCoordinator {

    interface Callbacks {
        void onDeviceWentOffline(String tenantId, String deviceId, Instant at);

        void onDeviceCameOnline(String tenantId, String deviceId, Instant at);
    }

    record DeviceRef(String tenantId, String deviceId, String deviceKey) {}

    private final double minFraction;
    private final double maxFraction;
    private final Duration minOffline;
    private final Duration maxOffline;
    private final Duration reconcileEvery;
    private final Duration reentryCooldown;

    private final Map<String, Instant> offlineUntilByDevice = new ConcurrentHashMap<>();
    private final Map<String, Instant> eligibleAfterByDevice = new ConcurrentHashMap<>();
    private volatile Instant lastReconcileAt = Instant.EPOCH;

    DummyDeviceOfflineCoordinator(
            double minFraction,
            double maxFraction,
            Duration minOffline,
            Duration maxOffline,
            Duration reconcileEvery,
            Duration reentryCooldown) {
        this.minFraction = Math.max(0, Math.min(minFraction, maxFraction));
        this.maxFraction = Math.max(this.minFraction, maxFraction);
        this.minOffline = minOffline;
        this.maxOffline = maxOffline.isNegative() || maxOffline.compareTo(minOffline) < 0
                ? minOffline
                : maxOffline;
        this.reconcileEvery = reconcileEvery.isZero() ? Duration.ofSeconds(10) : reconcileEvery;
        this.reentryCooldown =
                reentryCooldown.isNegative() ? Duration.ZERO : reentryCooldown;
    }

    boolean isOffline(String deviceKey, Instant now) {
        Instant until = offlineUntilByDevice.get(deviceKey);
        return until != null && now.isBefore(until);
    }

    int offlineCount() {
        return offlineUntilByDevice.size();
    }

    void reconcile(List<DeviceRef> devices, Instant now, Callbacks callbacks) {
        if (Duration.between(lastReconcileAt, now).compareTo(reconcileEvery) < 0) {
            return;
        }
        lastReconcileAt = now;

        expireFinishedWindows(devices, now, callbacks);

        int total = devices.size();
        if (total == 0) {
            return;
        }

        int maxOffline = Math.max(0, (int) Math.floor(total * maxFraction + 1e-9));
        int minOfflineTarget =
                Math.min(
                        maxOffline,
                        Math.max(0, (int) Math.ceil(total * minFraction - 1e-9)));
        int currentOffline = countOffline(devices, now);

        if (currentOffline >= maxOffline) {
            return;
        }

        int needed = Math.max(0, minOfflineTarget - currentOffline);
        if (needed == 0 && currentOffline < maxOffline && shouldMaintainChurn(now)) {
            needed = 1;
        }

        if (needed == 0) {
            return;
        }

        List<DeviceRef> candidates = eligibleCandidates(devices, now);
        candidates.sort(Comparator.comparingInt(device -> selectionScore(device, now)));

        int started = 0;
        for (DeviceRef device : candidates) {
            if (started >= needed || currentOffline + started >= maxOffline) {
                break;
            }
            startOfflineWindow(device, now, callbacks);
            started++;
        }
    }

    Duration offlineDurationFor(String tenantId, String deviceId, Instant startedAt) {
        long seed =
                mix64(
                        ((long) deviceId.hashCode() << 32)
                                ^ tenantId.hashCode()
                                ^ startedAt.getEpochSecond());
        long spanSeconds = maxOffline.minus(minOffline).getSeconds();
        long offset = Long.remainderUnsigned(seed, spanSeconds + 1);
        return minOffline.plusSeconds(offset);
    }

    private void expireFinishedWindows(List<DeviceRef> devices, Instant now, Callbacks callbacks) {
        for (DeviceRef device : devices) {
            Instant until = offlineUntilByDevice.get(device.deviceKey());
            if (until == null || now.isBefore(until)) {
                continue;
            }
            offlineUntilByDevice.remove(device.deviceKey());
            if (!reentryCooldown.isZero()) {
                eligibleAfterByDevice.put(device.deviceKey(), now.plus(reentryCooldown));
            }
            callbacks.onDeviceCameOnline(device.tenantId(), device.deviceId(), now);
        }
    }

    private int countOffline(List<DeviceRef> devices, Instant now) {
        int count = 0;
        for (DeviceRef device : devices) {
            if (isOffline(device.deviceKey(), now)) {
                count++;
            }
        }
        return count;
    }

    private List<DeviceRef> eligibleCandidates(List<DeviceRef> devices, Instant now) {
        List<DeviceRef> candidates = new ArrayList<>();
        for (DeviceRef device : devices) {
            if (isOffline(device.deviceKey(), now)) {
                continue;
            }
            Instant eligibleAfter = eligibleAfterByDevice.get(device.deviceKey());
            if (eligibleAfter != null && now.isBefore(eligibleAfter)) {
                continue;
            }
            candidates.add(device);
        }
        return candidates;
    }

    private void startOfflineWindow(DeviceRef device, Instant now, Callbacks callbacks) {
        Duration duration = offlineDurationFor(device.tenantId(), device.deviceId(), now);
        offlineUntilByDevice.put(device.deviceKey(), now.plus(duration));
        eligibleAfterByDevice.remove(device.deviceKey());
        callbacks.onDeviceWentOffline(device.tenantId(), device.deviceId(), now);
    }

    private static int selectionScore(DeviceRef device, Instant now) {
        long slot = now.getEpochSecond() / reconcileSlotSeconds();
        return (int)
                (mix64(device.deviceKey().hashCode() ^ (slot << 16)) & 0x7fffffff);
    }

    private static boolean shouldMaintainChurn(Instant now) {
        return (now.getEpochSecond() / reconcileSlotSeconds()) % 3 == 0;
    }

    private static int reconcileSlotSeconds() {
        return 60;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
