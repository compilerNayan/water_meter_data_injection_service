package com.vswitch.datainjection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.DeviceFacade;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentCompletionServiceTest {

    @Mock private PreEnrollRepository preEnrollRepository;
    @Mock private UnitService unitService;
    @Mock private DeviceFacade deviceFacade;
    @Mock private TenantMetadataService tenantMetadataService;

    @InjectMocks private EnrollmentCompletionService service;

    @Test
    void marksPendingUnitEnrolled() {
        UnitRecord pending =
                new UnitRecord(
                        "wm-WM000001",
                        "k3m9x2a",
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "Resident",
                        "+1",
                        "",
                        UnitRecord.STATUS_PENDING,
                        "D205-1234",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");

        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(java.util.Optional.of(pending));
        when(preEnrollRepository.findBySerialNumber("WM000001"))
                .thenReturn(
                        java.util.Optional.of(
                                new DevicePreEnrollRecord(
                                        "WM000001",
                                        "k3m9x2a",
                                        PreEnrollRepository.STATUS_PENDING,
                                        "2026-01-01T00:00:00Z",
                                        "2026-01-01T01:00:00Z",
                                        "user-1",
                                        null)));

        service.onEnrolled("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z");

        verify(unitService).markEnrollmentComplete("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z");
        verify(deviceFacade).initializeDeviceConfig("WM000001", "k3m9x2a");
        verify(deviceFacade).initializeDeviceState("WM000001", "k3m9x2a");
        verify(tenantMetadataService).recomputeAndPersist("k3m9x2a");
    }

    @Test
    void idempotentWhenAlreadyEnrolled() {
        UnitRecord enrolled =
                new UnitRecord(
                        "wm-WM000001",
                        "k3m9x2a",
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

        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(java.util.Optional.of(enrolled));

        service.onEnrolled("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z");

        verify(unitService, never()).markEnrollmentComplete("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z");
        verify(deviceFacade, never()).initializeDeviceState("WM000001", "k3m9x2a");
    }

    @Test
    void rejectsTenantMismatchOnPreEnroll() {
        UnitRecord pending =
                new UnitRecord(
                        "wm-WM000001",
                        "k3m9x2a",
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "Resident",
                        "+1",
                        "",
                        UnitRecord.STATUS_PENDING,
                        "D205-1234",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");

        when(unitService.findByTenantAndDeviceId("k3m9x2a", "WM000001"))
                .thenReturn(java.util.Optional.of(pending));
        when(preEnrollRepository.findBySerialNumber("WM000001"))
                .thenReturn(
                        java.util.Optional.of(
                                new DevicePreEnrollRecord(
                                        "WM000001",
                                        "other-tenant",
                                        PreEnrollRepository.STATUS_PENDING,
                                        "2026-01-01T00:00:00Z",
                                        "2026-01-01T01:00:00Z",
                                        "user-1",
                                        null)));

        assertThrows(
                ResponseStatusException.class,
                () -> service.onEnrolled("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z"));

        verify(unitService, never()).markEnrollmentComplete("k3m9x2a", "WM000001", "2026-06-09T10:00:00Z");
    }
}
