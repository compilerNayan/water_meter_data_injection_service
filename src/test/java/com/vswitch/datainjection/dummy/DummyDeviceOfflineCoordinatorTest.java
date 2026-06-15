package com.vswitch.datainjection.dummy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyDeviceOfflineCoordinatorTest {

    @Test
    void offlineDurationStaysWithinTwoToFiveMinutes() {
        DummyDeviceOfflineCoordinator coordinator =
                new DummyDeviceOfflineCoordinator(
                        0.03,
                        0.06,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(5));

        Instant start = Instant.parse("2026-06-15T12:00:00Z");
        for (int i = 0; i < 50; i++) {
            Duration duration =
                    coordinator.offlineDurationFor("tenant-1", "WM" + i, start.plusSeconds(i * 17L));
            assertTrue(duration.compareTo(Duration.ofMinutes(2)) >= 0);
            assertTrue(duration.compareTo(Duration.ofMinutes(5)) <= 0);
        }
    }

    @Test
    void keepsOfflineCountWithinSixPercentForLargeFleet() {
        DummyDeviceOfflineCoordinator coordinator =
                new DummyDeviceOfflineCoordinator(
                        0.03,
                        0.06,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(10),
                        Duration.ZERO);

        List<DummyDeviceOfflineCoordinator.DeviceRef> devices = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            devices.add(new DummyDeviceOfflineCoordinator.DeviceRef("tenant-1", "WM" + i, "tenant-1#WM" + i));
        }

        AtomicInteger maxObservedOffline = new AtomicInteger();
        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        for (int step = 0; step < 120; step++) {
            coordinator.reconcile(devices, now, noopCallbacks());
            int offline = coordinator.offlineCount();
            maxObservedOffline.updateAndGet(current -> Math.max(current, offline));
            assertTrue(offline <= 6, "offline count was " + offline);
            now = now.plusSeconds(10);
        }

        assertTrue(maxObservedOffline.get() >= 3, "expected some offline devices over time");
    }

    @Test
    void deviceStaysOfflineForAtLeastTwoMinutes() {
        DummyDeviceOfflineCoordinator coordinator =
                new DummyDeviceOfflineCoordinator(
                        0.03,
                        0.06,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(1),
                        Duration.ZERO);

        List<DummyDeviceOfflineCoordinator.DeviceRef> devices = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            devices.add(
                    new DummyDeviceOfflineCoordinator.DeviceRef(
                            "tenant-1", "WM" + i, "tenant-1#WM" + i));
        }

        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        Instant offlineStarted = null;
        String offlineKey = null;
        for (int second = 0; second < 600; second++) {
            coordinator.reconcile(devices, now, noopCallbacks());
            if (offlineKey == null) {
                for (DummyDeviceOfflineCoordinator.DeviceRef device : devices) {
                    if (coordinator.isOffline(device.deviceKey(), now)) {
                        offlineKey = device.deviceKey();
                        offlineStarted = now;
                        break;
                    }
                }
            } else if (!coordinator.isOffline(offlineKey, now)) {
                Duration offlineFor = Duration.between(offlineStarted, now);
                assertTrue(offlineFor.compareTo(Duration.ofMinutes(2)) >= 0);
                assertTrue(offlineFor.compareTo(Duration.ofMinutes(5)) <= 0);
                return;
            }
            now = now.plusSeconds(1);
        }

        assertTrue(offlineStarted != null, "at least one device should have gone offline");
    }

    private static DummyDeviceOfflineCoordinator.Callbacks noopCallbacks() {
        return new DummyDeviceOfflineCoordinator.Callbacks() {
            @Override
            public void onDeviceWentOffline(String tenantId, String deviceId, Instant at) {}

            @Override
            public void onDeviceCameOnline(String tenantId, String deviceId, Instant at) {}
        };
    }
}
