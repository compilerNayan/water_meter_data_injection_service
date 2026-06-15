package com.vswitch.datainjection;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.DeviceStore;
import com.vswitch.datainjection.device.stream.DeviceLiveTelemetryStore;
import com.vswitch.datainjection.device.stream.DevicePresenceService;
import com.vswitch.datainjection.device.stream.WaterFlowLiveBroadcastGate;
import com.vswitch.datainjection.dummy.DummyDeviceTelemetrySimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDeletionServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String OWNER_ID = "owner-1";

    @Mock private TenantService tenantService;
    @Mock private UnitService unitService;
    @Mock private DeviceStore deviceStore;
    @Mock private PreEnrollRepository preEnrollRepository;
    @Mock private DummyDeviceRepository dummyDeviceRepository;
    @Mock private UserService userService;
    @Mock private DevicePresenceService devicePresenceService;
    @Mock private DeviceLiveTelemetryStore liveTelemetryStore;
    @Mock private WaterFlowLiveBroadcastGate waterFlowLiveBroadcastGate;
    @Mock private DummyDeviceTelemetrySimulator dummyDeviceTelemetrySimulator;
    @Mock private CognitoUserDeletionService cognitoUserDeletionService;

    private TenantDeletionService service;

    @BeforeEach
    void setUp() {
        service =
                new TenantDeletionService(
                        tenantService,
                        unitService,
                        deviceStore,
                        preEnrollRepository,
                        dummyDeviceRepository,
                        userService,
                        devicePresenceService,
                        liveTelemetryStore,
                        waterFlowLiveBroadcastGate,
                        dummyDeviceTelemetrySimulator,
                        cognitoUserDeletionService);
    }

    @Test
    void deletesTenantDataAndEvictsDummyTelemetry() {
        UnitRecord unit =
                new UnitRecord(
                        "wm-WM000001",
                        TENANT_ID,
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "Resident",
                        "+1",
                        "",
                        UnitRecord.STATUS_ENROLLED,
                        "D205-1234",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");
        UserRecord owner =
                new UserRecord(
                        OWNER_ID,
                        "owner@example.com",
                        "",
                        "Owner",
                        "User",
                        "Owner User",
                        TENANT_ID,
                        true,
                        true,
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");
        TenantRecord tenant =
                new TenantRecord(
                        TENANT_ID,
                        "Tower A",
                        OWNER_ID,
                        "{\"blocks\":[]}",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z",
                        "",
                        "",
                        "");

        doNothing().when(userService).requireTenantOwner(OWNER_ID, TENANT_ID);
        when(tenantService.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(unitService.listUnitRecords(TENANT_ID)).thenReturn(List.of(unit));
        when(preEnrollRepository.listByTenant(TENANT_ID))
                .thenReturn(
                        List.of(
                                new DevicePreEnrollRecord(
                                        "WM000002",
                                        TENANT_ID,
                                        PreEnrollRepository.STATUS_PENDING,
                                        "2026-01-01T00:00:00Z",
                                        null,
                                        OWNER_ID,
                                        null)));
        when(dummyDeviceRepository.listByTenant(TENANT_ID))
                .thenReturn(
                        List.of(
                                DummyDeviceRecord.create(
                                        TENANT_ID, "WM000003", "2026-01-01T00:00:00Z", OWNER_ID)));
        when(preEnrollRepository.deleteAllForTenant(TENANT_ID)).thenReturn(1);
        when(dummyDeviceRepository.deleteAllForTenant(TENANT_ID)).thenReturn(1);
        when(userService.listByTenant(TENANT_ID)).thenReturn(List.of(owner));
        when(cognitoUserDeletionService.deleteUser(owner)).thenReturn(true);

        TenantDeletionResponse response = service.deleteTenant(OWNER_ID, TENANT_ID);

        verify(unitService).deleteUnit(unit.unitId());
        verify(deviceStore).deleteAllDeviceData("WM000001");
        verify(deviceStore).deleteAllDeviceData("WM000002");
        verify(deviceStore).deleteAllDeviceData("WM000003");
        verify(devicePresenceService).clear("WM000001");
        verify(devicePresenceService).clear("WM000002");
        verify(devicePresenceService).clear("WM000003");
        verify(liveTelemetryStore).clear("WM000001");
        verify(waterFlowLiveBroadcastGate).clearDevice("WM000001");
        verify(preEnrollRepository).deleteAllForTenant(TENANT_ID);
        verify(dummyDeviceRepository).deleteAllForTenant(TENANT_ID);
        verify(cognitoUserDeletionService).deleteUser(owner);
        verify(userService).deleteUser(OWNER_ID);
        verify(tenantService).deleteTenant(TENANT_ID);
        verify(dummyDeviceTelemetrySimulator).evictTenant(TENANT_ID);

        assertEquals(TENANT_ID, response.tenantId());
        assertEquals(1, response.unitsDeleted());
        assertEquals(3, response.deviceDataSetsDeleted());
        assertEquals(1, response.preEnrollmentsDeleted());
        assertEquals(1, response.dummyDevicesDeleted());
        assertEquals(1, response.usersDeleted());
        assertEquals(1, response.cognitoUsersDeleted());
        assertTrue(response.tenantDeleted());
    }

    @Test
    void continuesWhenDummyDeviceRegistryUnavailable() {
        UnitRecord unit =
                new UnitRecord(
                        "wm-WM000001",
                        TENANT_ID,
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "Resident",
                        "+1",
                        "",
                        UnitRecord.STATUS_ENROLLED,
                        "D205-1234",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");
        UserRecord owner =
                new UserRecord(
                        OWNER_ID,
                        "owner@example.com",
                        "",
                        "Owner",
                        "User",
                        "Owner User",
                        TENANT_ID,
                        true,
                        true,
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");
        TenantRecord tenant =
                new TenantRecord(
                        TENANT_ID,
                        "Tower A",
                        OWNER_ID,
                        "{\"blocks\":[]}",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z",
                        "",
                        "",
                        "");

        doNothing().when(userService).requireTenantOwner(OWNER_ID, TENANT_ID);
        when(tenantService.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(unitService.listUnitRecords(TENANT_ID)).thenReturn(List.of(unit));
        when(preEnrollRepository.listByTenant(TENANT_ID)).thenReturn(List.of());
        when(dummyDeviceRepository.listByTenant(TENANT_ID))
                .thenThrow(
                        new RuntimeException(
                                "not authorized to perform: dynamodb:Scan on WaterMeterDummyDevices"));
        when(preEnrollRepository.deleteAllForTenant(TENANT_ID)).thenReturn(0);
        when(dummyDeviceRepository.deleteAllForTenant(TENANT_ID)).thenReturn(0);
        when(userService.listByTenant(TENANT_ID)).thenReturn(List.of(owner));

        TenantDeletionResponse response = service.deleteTenant(OWNER_ID, TENANT_ID);

        verify(deviceStore).deleteAllDeviceData("WM000001");
        verify(tenantService).deleteTenant(TENANT_ID);
        assertTrue(response.tenantDeleted());
        assertEquals(1, response.unitsDeleted());
    }

    @Test
    void rejectsWhenTenantMissing() {
        doNothing().when(userService).requireTenantOwner(OWNER_ID, TENANT_ID);
        when(tenantService.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class, () -> service.deleteTenant(OWNER_ID, TENANT_ID));

        verify(tenantService, never()).deleteTenant(TENANT_ID);
        verify(dummyDeviceTelemetrySimulator, never()).evictTenant(TENANT_ID);
    }
}
