package com.vswitch.datainjection.device.stream;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vswitch.datainjection.DevicePreEnrollService;
import com.vswitch.datainjection.DeviceTenantLookupResponse;
import com.vswitch.datainjection.device.DeviceFacade;

/**
 * Marks devices online/offline from TCP stream socket lifecycle (not from water pulses).
 */
@Service
public class DeviceStreamPresenceCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamPresenceCoordinator.class);

    private final DevicePresenceService presenceService;
    private final DevicePreEnrollService preEnrollService;
    private final DeviceFacade deviceFacade;

    DeviceStreamPresenceCoordinator(
            DevicePresenceService presenceService,
            DevicePreEnrollService preEnrollService,
            DeviceFacade deviceFacade) {
        this.presenceService = presenceService;
        this.preEnrollService = preEnrollService;
        this.deviceFacade = deviceFacade;
    }

    public void onSerialBound(String serialNumber) {
        resolveIdentity(serialNumber)
                .ifPresent(
                        identity -> {
                            Instant now = Instant.now();
                            presenceService.markOnline(identity.tenantId(), identity.deviceId(), now);
                            try {
                                deviceFacade.touchHeartbeat(identity.tenantId(), identity.deviceId(), now);
                            } catch (Exception e) {
                                log.debug(
                                        "Failed to touch heartbeat on socket connect for {}/{}",
                                        identity.tenantId(),
                                        identity.deviceId(),
                                        e);
                            }
                            log.info(
                                    "Device stream online {}/{} (serial bound)",
                                    identity.tenantId(),
                                    identity.deviceId());
                        });
    }

    public void onSerialUnbound(String serialNumber) {
        resolveIdentity(serialNumber)
                .ifPresent(
                        identity -> {
                            Instant now = Instant.now();
                            presenceService.markOffline(identity.tenantId(), identity.deviceId(), now);
                            try {
                                deviceFacade.markDeviceOffline(identity.tenantId(), identity.deviceId());
                            } catch (Exception e) {
                                log.debug(
                                        "Failed to persist offline on socket disconnect for {}/{}",
                                        identity.tenantId(),
                                        identity.deviceId(),
                                        e);
                            }
                            log.info(
                                    "Device stream offline {}/{} (socket closed)",
                                    identity.tenantId(),
                                    identity.deviceId());
                        });
    }

    private Optional<DeviceIdentity> resolveIdentity(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return Optional.empty();
        }
        String deviceId = DeviceLiveTelemetryStore.normalizeDeviceId(serialNumber);
        try {
            DeviceTenantLookupResponse lookup = preEnrollService.lookupTenantBySerial(deviceId);
            return Optional.of(new DeviceIdentity(lookup.tenantId().trim(), deviceId));
        } catch (Exception e) {
            log.debug("No tenant mapping for serial {}: {}", deviceId, e.getMessage());
            return Optional.empty();
        }
    }

    private record DeviceIdentity(String tenantId, String deviceId) {}
}
