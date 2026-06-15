package com.vswitch.datainjection;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.dummy.DummyDeviceHistoricalBackfillService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePreEnrollServiceDummyEnrollTest {

    @Mock private software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamoDbClient;
    @Mock private UserService userService;
    @Mock private PreEnrollRepository preEnrollRepository;
    @Mock private UnitService unitService;
    @Mock private EnrollmentCompletionService enrollmentCompletionService;
    @Mock private DummyDeviceRepository dummyDeviceRepository;
    @Mock private DummyDeviceHistoricalBackfillService dummyHistoricalBackfillService;
    @Mock private ExecutorService dummyBulkEnrollExecutor;

    private DevicePreEnrollService service;

    @BeforeEach
    void setUp() {
        service =
                new DevicePreEnrollService(
                        dynamoDbClient,
                        userService,
                        preEnrollRepository,
                        unitService,
                        enrollmentCompletionService,
                        dummyDeviceRepository,
                        dummyHistoricalBackfillService,
                        dummyBulkEnrollExecutor,
                        "WaterMeterDevicePreEnrollments",
                        1000);
    }

    @Test
    void dummyEnrollMarksPreEnrollEnrolledAndCompletesPendingUnit() {
        when(userService.findById("user-1"))
                .thenReturn(
                        Optional.of(
                                new UserRecord(
                                        "user-1",
                                        "a@test.com",
                                        "",
                                        "A",
                                        "B",
                                        "A B",
                                        "tenant-1",
                                        true,
                                        false,
                                        Instant.now().toString(),
                                        Instant.now().toString())));
        when(unitService.findByTenantAndDeviceId("tenant-1", "WM001"))
                .thenReturn(
                        Optional.of(
                                new UnitRecord(
                                        "wm-WM001",
                                        "tenant-1",
                                        "WM001",
                                        "D205",
                                        "D205",
                                        "1",
                                        "A",
                                        "East",
                                        "Resident",
                                        "+911",
                                        "",
                                        UnitRecord.STATUS_PENDING,
                                        "D205-1234",
                                        "2026-01-01T00:00:00Z",
                                        "2026-01-01T00:00:00Z")));

        DevicePreEnrollResponse response =
                service.dummyEnroll(
                        "user-1",
                        "tenant-1",
                        new DevicePreEnrollRequest("WM001"));

        assertEquals("tenant-1", response.tenantId());
        assertEquals("WM001", response.serialNumber());
        assertEquals(PreEnrollRepository.STATUS_ENROLLED, response.status());

        ArgumentCaptor<DevicePreEnrollRecord> captor =
                ArgumentCaptor.forClass(DevicePreEnrollRecord.class);
        verify(preEnrollRepository).save(captor.capture());
        assertEquals(PreEnrollRepository.STATUS_ENROLLED, captor.getValue().status());
        assertTrue(captor.getValue().enrolledAt() != null && !captor.getValue().enrolledAt().isBlank());
        verify(dummyDeviceRepository)
                .register(eq("tenant-1"), eq("WM001"), org.mockito.ArgumentMatchers.anyString(), eq("user-1"));
        verify(unitService)
                .upsertDummyUnitLocation(
                        eq("tenant-1"), eq("WM001"), eq(null), eq(null), eq(null));
        verify(enrollmentCompletionService)
                .onEnrolled(eq("tenant-1"), eq("WM001"), org.mockito.ArgumentMatchers.anyString());
        verify(dummyHistoricalBackfillService).scheduleBackfill("tenant-1", "WM001");
    }

    @Test
    void dummyEnrollWithoutUnitOnlySavesPreEnroll() {
        when(userService.findById("user-1"))
                .thenReturn(
                        Optional.of(
                                new UserRecord(
                                        "user-1",
                                        "a@test.com",
                                        "",
                                        "A",
                                        "B",
                                        "A B",
                                        "tenant-1",
                                        true,
                                        false,
                                        Instant.now().toString(),
                                        Instant.now().toString())));
        when(unitService.findByTenantAndDeviceId("tenant-1", "WM001")).thenReturn(Optional.empty());

        DevicePreEnrollResponse response =
                service.dummyEnroll(
                        "user-1", "tenant-1", new DevicePreEnrollRequest("WM001"));

        assertEquals(PreEnrollRepository.STATUS_ENROLLED, response.status());
        verify(preEnrollRepository).save(org.mockito.ArgumentMatchers.any());
        verify(dummyDeviceRepository)
                .register(eq("tenant-1"), eq("WM001"), org.mockito.ArgumentMatchers.anyString(), eq("user-1"));
        verify(unitService)
                .upsertDummyUnitLocation(
                        eq("tenant-1"), eq("WM001"), eq(null), eq(null), eq(null));
        verify(dummyHistoricalBackfillService).scheduleBackfill("tenant-1", "WM001");
        verify(enrollmentCompletionService, org.mockito.Mockito.never())
                .onEnrolled(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dummyEnrollPassesLocationFieldsToUnitService() {
        when(userService.findById("user-1"))
                .thenReturn(
                        Optional.of(
                                new UserRecord(
                                        "user-1",
                                        "a@test.com",
                                        "",
                                        "A",
                                        "B",
                                        "A B",
                                        "tenant-1",
                                        true,
                                        false,
                                        Instant.now().toString(),
                                        Instant.now().toString())));
        when(unitService.findByTenantAndDeviceId("tenant-1", "WM003")).thenReturn(Optional.empty());

        service.dummyEnroll(
                "user-1",
                "tenant-1",
                new DevicePreEnrollRequest("WM003", "Block-A", "North", "7"));

        verify(unitService)
                .upsertDummyUnitLocation(
                        "tenant-1", "WM003", "Block-A", "North", "7");
    }
}
