package com.vswitch.datainjection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        int unitsDeleted = 0;
        int deviceDataSetsDeleted = 0;
        Set<String> deviceIds = new HashSet<>();

        for (UnitRecord unit : unitService.listUnitRecords(tenantId)) {
            deviceIds.add(unit.deviceId());
            unitService.deleteUnit(unit.unitId());
            unitsDeleted++;
        }

        for (var preEnroll : preEnrollRepository.listByTenant(tenantId)) {
            deviceIds.add(preEnroll.serialNumber());
        }
        for (DummyDeviceRecord dummy : dummyDeviceRepository.listByTenant(tenantId)) {
            deviceIds.add(dummy.serialNumber());
        }

        for (String deviceId : deviceIds) {
            deviceStore.deleteAllDeviceData(deviceId);
            clearInMemoryDeviceState(deviceId);
            deviceDataSetsDeleted++;
        }

        int preEnrollmentsDeleted = preEnrollRepository.deleteAllForTenant(tenantId);
        int dummyDevicesDeleted = dummyDeviceRepository.deleteAllForTenant(tenantId);

        List<UserRecord> users = userService.listByTenant(tenantId);
        int cognitoUsersDeleted = 0;
        for (UserRecord user : users) {
            if (cognitoUserDeletionService != null && cognitoUserDeletionService.deleteUser(user)) {
                cognitoUsersDeleted++;
            }
            userService.deleteUser(user.userId());
        }

        tenantService.deleteTenant(tenantId);
        if (dummyDeviceTelemetrySimulator != null) {
            dummyDeviceTelemetrySimulator.evictTenant(tenantId);
        }

        log.warn(
                "Deleted tenant {} (units={}, devices={}, users={}, cognito={})",
                tenantId,
                unitsDeleted,
                deviceDataSetsDeleted,
                users.size(),
                cognitoUsersDeleted);

        return new TenantDeletionResponse(
                tenantId,
                unitsDeleted,
                deviceDataSetsDeleted,
                preEnrollmentsDeleted,
                dummyDevicesDeleted,
                users.size(),
                cognitoUsersDeleted,
                true);
    }

    private void clearInMemoryDeviceState(String deviceId) {
        devicePresenceService.clear(deviceId);
        liveTelemetryStore.clear(deviceId);
        waterFlowLiveBroadcastGate.clearDevice(deviceId);
    }
}
