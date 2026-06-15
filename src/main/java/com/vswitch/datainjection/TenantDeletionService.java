package com.vswitch.datainjection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.DeviceStore;
import com.vswitch.datainjection.device.stream.DeviceLiveTelemetryStore;
import com.vswitch.datainjection.device.stream.DevicePresenceService;
import com.vswitch.datainjection.device.stream.WaterFlowLiveBroadcastGate;
import com.vswitch.datainjection.dummy.DummyDeviceTelemetrySimulator;

@Service
public class TenantDeletionService {

    private static final Logger log = LoggerFactory.getLogger(TenantDeletionService.class);

    private final TenantService tenantService;
    private final UnitService unitService;
    private final DeviceStore deviceStore;
    private final PreEnrollRepository preEnrollRepository;
    private final DummyDeviceRepository dummyDeviceRepository;
    private final UserService userService;
    private final DevicePresenceService devicePresenceService;
    private final DeviceLiveTelemetryStore liveTelemetryStore;
    private final WaterFlowLiveBroadcastGate waterFlowLiveBroadcastGate;
    private final DummyDeviceTelemetrySimulator dummyDeviceTelemetrySimulator;
    private final CognitoUserDeletionService cognitoUserDeletionService;

    TenantDeletionService(
            TenantService tenantService,
            UnitService unitService,
            DeviceStore deviceStore,
            PreEnrollRepository preEnrollRepository,
            DummyDeviceRepository dummyDeviceRepository,
            UserService userService,
            DevicePresenceService devicePresenceService,
            DeviceLiveTelemetryStore liveTelemetryStore,
            WaterFlowLiveBroadcastGate waterFlowLiveBroadcastGate,
            @Autowired(required = false) DummyDeviceTelemetrySimulator dummyDeviceTelemetrySimulator,
            @Autowired(required = false) CognitoUserDeletionService cognitoUserDeletionService) {
        this.tenantService = tenantService;
        this.unitService = unitService;
        this.deviceStore = deviceStore;
        this.preEnrollRepository = preEnrollRepository;
        this.dummyDeviceRepository = dummyDeviceRepository;
        this.userService = userService;
        this.devicePresenceService = devicePresenceService;
        this.liveTelemetryStore = liveTelemetryStore;
        this.waterFlowLiveBroadcastGate = waterFlowLiveBroadcastGate;
        this.dummyDeviceTelemetrySimulator = dummyDeviceTelemetrySimulator;
        this.cognitoUserDeletionService = cognitoUserDeletionService;
    }

    TenantDeletionResponse deleteTenant(String requesterUserId, String tenantId) {
        userService.requireTenantOwner(requesterUserId, tenantId);
        tenantService
                .findById(tenantId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Tenant not found"));

        log.warn("Deleting tenant {} requested by {}", tenantId, requesterUserId);

        if (dummyDeviceTelemetrySimulator != null) {
            dummyDeviceTelemetrySimulator.evictTenant(tenantId);
        }

        Set<String> deviceIds = new HashSet<>();
        List<UnitRecord> units = unitService.listUnitRecords(tenantId);
        for (UnitRecord unit : units) {
            deviceIds.add(unit.deviceId());
        }
        collectPreEnrollDeviceIds(tenantId, deviceIds);
        collectDummyDeviceIds(tenantId, deviceIds);
        log.warn(
                "Tenant {} wipe: collected {} device ids from {} units",
                tenantId,
                deviceIds.size(),
                units.size());

        List<UserRecord> users = listUsersForTenant(tenantId, requesterUserId);
        int cognitoUsersDeleted = 0;
        int usersDeleted = 0;
        for (UserRecord user : users) {
            try {
                if (cognitoUserDeletionService != null
                        && cognitoUserDeletionService.deleteUser(user)) {
                    cognitoUsersDeleted++;
                }
                userService.deleteUser(user.userId());
                usersDeleted++;
            } catch (Exception e) {
                log.warn(
                        "Could not delete user {} during tenant {} wipe: {}",
                        user.userId(),
                        tenantId,
                        e.toString());
            }
        }

        tenantService.deleteTenant(tenantId);
        log.warn("Deleted tenant record {}", tenantId);

        int unitsDeleted = 0;
        for (UnitRecord unit : units) {
            try {
                unitService.deleteUnit(unit.unitId());
                unitsDeleted++;
            } catch (Exception e) {
                log.warn(
                        "Could not delete unit {} during tenant {} wipe: {}",
                        unit.unitId(),
                        tenantId,
                        e.toString());
            }
        }

        int preEnrollmentsDeleted = deletePreEnrollmentsForTenant(tenantId);
        int dummyDevicesDeleted = deleteDummyDevicesForTenant(tenantId);

        int deviceDataSetsDeleted = scheduleDeviceDataDeletion(tenantId, deviceIds);

        log.warn(
                "Deleted tenant {} (units={}, deviceCleanupScheduled={}, users={}, cognito={})",
                tenantId,
                unitsDeleted,
                deviceDataSetsDeleted,
                usersDeleted,
                cognitoUsersDeleted);

        return new TenantDeletionResponse(
                tenantId,
                unitsDeleted,
                deviceDataSetsDeleted,
                preEnrollmentsDeleted,
                dummyDevicesDeleted,
                usersDeleted,
                cognitoUsersDeleted,
                true);
    }

    private int scheduleDeviceDataDeletion(String tenantId, Set<String> deviceIds) {
        if (deviceIds.isEmpty()) {
            return 0;
        }
        List<String> deviceIdList = new ArrayList<>(deviceIds);
        CompletableFuture.runAsync(
                () -> {
                    int deleted = 0;
                    for (String deviceId : deviceIdList) {
                        try {
                            deviceStore.deleteAllDeviceData(deviceId);
                            clearInMemoryDeviceState(deviceId);
                            deleted++;
                        } catch (Exception e) {
                            log.warn(
                                    "Could not delete device data for {} during tenant {} wipe: {}",
                                    deviceId,
                                    tenantId,
                                    e.toString());
                        }
                    }
                    log.warn(
                            "Tenant {} background device cleanup finished ({}/{} devices)",
                            tenantId,
                            deleted,
                            deviceIdList.size());
                });
        return deviceIdList.size();
    }

    private void clearInMemoryDeviceState(String deviceId) {
        devicePresenceService.clear(deviceId);
        liveTelemetryStore.clear(deviceId);
        waterFlowLiveBroadcastGate.clearDevice(deviceId);
    }

    private void collectPreEnrollDeviceIds(String tenantId, Set<String> deviceIds) {
        try {
            for (var preEnroll : preEnrollRepository.listByTenant(tenantId)) {
                deviceIds.add(preEnroll.serialNumber());
            }
        } catch (Exception e) {
            log.warn(
                    "Could not list pre-enrollments for tenant {} during wipe: {}",
                    tenantId,
                    e.toString());
        }
    }

    private void collectDummyDeviceIds(String tenantId, Set<String> deviceIds) {
        try {
            for (DummyDeviceRecord dummy : dummyDeviceRepository.listByTenant(tenantId)) {
                deviceIds.add(dummy.serialNumber());
            }
        } catch (Exception e) {
            log.warn(
                    "Could not list dummy devices for tenant {} during wipe: {}",
                    tenantId,
                    e.toString());
        }
    }

    private int deletePreEnrollmentsForTenant(String tenantId) {
        try {
            return preEnrollRepository.deleteAllForTenant(tenantId);
        } catch (Exception e) {
            log.warn(
                    "Could not delete pre-enrollments for tenant {} during wipe: {}",
                    tenantId,
                    e.toString());
            return 0;
        }
    }

    private int deleteDummyDevicesForTenant(String tenantId) {
        try {
            return dummyDeviceRepository.deleteAllForTenant(tenantId);
        } catch (Exception e) {
            log.warn(
                    "Could not delete dummy-device registry for tenant {} during wipe: {}",
                    tenantId,
                    e.toString());
            return 0;
        }
    }

    private List<UserRecord> listUsersForTenant(String tenantId, String requesterUserId) {
        try {
            return userService.listByTenant(tenantId);
        } catch (Exception e) {
            log.warn(
                    "Could not scan users for tenant {} during wipe: {}",
                    tenantId,
                    e.toString());
            try {
                UserRecord requester =
                        userService
                                .findById(requesterUserId)
                                .orElseThrow(
                                        () ->
                                                new ResponseStatusException(
                                                        HttpStatus.NOT_FOUND, "User not found"));
                if (tenantId.equals(requester.tenantId())) {
                    return List.of(requester);
                }
            } catch (Exception ignored) {
                // fall through
            }
            return List.of();
        }
    }
}
