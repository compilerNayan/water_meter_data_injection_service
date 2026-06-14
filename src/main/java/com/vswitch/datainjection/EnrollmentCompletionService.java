package com.vswitch.datainjection;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.DeviceFacade;

@Service
public class EnrollmentCompletionService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentCompletionService.class);

    private final PreEnrollRepository preEnrollRepository;
    private final UnitService unitService;
    private final DeviceFacade deviceFacade;
    private final TenantMetadataService tenantMetadataService;

    EnrollmentCompletionService(
            PreEnrollRepository preEnrollRepository,
            UnitService unitService,
            DeviceFacade deviceFacade,
            @Autowired @Lazy TenantMetadataService tenantMetadataService) {
        this.preEnrollRepository = preEnrollRepository;
        this.unitService = unitService;
        this.deviceFacade = deviceFacade;
        this.tenantMetadataService = tenantMetadataService;
    }

    public void onEnrolled(String tenantId, String deviceId, String enrolledAt) {
        String serial = deviceId.trim();
        String tenant = tenantId.trim();

        UnitRecord unit =
                unitService
                        .findByTenantAndDeviceId(tenant, serial)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Unit not found for enrollment"));

        if (UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus())) {
            log.debug("Device {} already enrolled for tenant {}", serial, tenant);
            return;
        }

        if (!UnitRecord.STATUS_PENDING.equals(unit.enrollmentStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unit enrollment status is not pending: " + unit.enrollmentStatus());
        }

        preEnrollRepository
                .findBySerialNumber(serial)
                .ifPresentOrElse(
                        preEnroll -> validatePreEnroll(preEnroll, tenant),
                        () ->
                                log.warn(
                                        "No pre-enroll record for serial {} during lifecycle/enrolled",
                                        serial));

        String completedAt =
                enrolledAt != null && !enrolledAt.isBlank()
                        ? enrolledAt
                        : Instant.now().toString();

        unitService.markEnrollmentComplete(tenant, serial, completedAt);
        markPreEnrollComplete(serial, tenant, completedAt);

        deviceFacade.initializeDeviceConfig(serial, tenant);
        deviceFacade.initializeDeviceState(serial, tenant);

        tenantMetadataService.recomputeAndPersist(tenant);
        log.info("Enrollment complete for device {} tenant {}", serial, tenant);
    }

    private static void validatePreEnroll(DevicePreEnrollRecord preEnroll, String tenantId) {
        if (!tenantId.equals(preEnroll.tenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Pre-enroll tenant does not match enrollment payload");
        }
        if (PreEnrollRepository.STATUS_ENROLLED.equals(preEnroll.status())) {
            return;
        }
        if (!PreEnrollRepository.STATUS_PENDING.equals(preEnroll.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Pre-enroll status is not pending");
        }
    }

    private void markPreEnrollComplete(String serial, String tenantId, String enrolledAt) {
        preEnrollRepository
                .findBySerialNumber(serial)
                .ifPresent(
                        existing -> {
                            if (PreEnrollRepository.STATUS_ENROLLED.equals(existing.status())) {
                                return;
                            }
                            preEnrollRepository.save(
                                    new DevicePreEnrollRecord(
                                            serial,
                                            tenantId,
                                            PreEnrollRepository.STATUS_ENROLLED,
                                            existing.createdAt(),
                                            existing.expiresAt(),
                                            existing.createdByUserId(),
                                            enrolledAt));
                        });
    }
}
