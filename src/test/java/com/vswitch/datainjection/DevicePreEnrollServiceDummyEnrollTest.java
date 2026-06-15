package com.vswitch.datainjection;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.vswitch.datainjection.dummy.DummyDeviceHistoricalBackfillService;

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

    @InjectMocks private DevicePreEnrollService service;

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
        verify(dummyHistoricalBackfillService).scheduleBackfill("tenant-1", "WM001");
        verify(enrollmentCompletionService, org.mockito.Mockito.never())
                .onEnrolled(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}
