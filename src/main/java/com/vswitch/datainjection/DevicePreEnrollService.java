package com.vswitch.datainjection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class DevicePreEnrollService {

    private static final String STATUS_PENDING = "pending";

    private final DynamoDbClient dynamoDbClient;
    private final UserService userService;
    private final PreEnrollRepository preEnrollRepository;
    private final UnitService unitService;
    private final EnrollmentCompletionService enrollmentCompletionService;
    private final DummyDeviceRepository dummyDeviceRepository;
    private final String tableName;

    DevicePreEnrollService(
            DynamoDbClient dynamoDbClient,
            UserService userService,
            PreEnrollRepository preEnrollRepository,
            UnitService unitService,
            EnrollmentCompletionService enrollmentCompletionService,
            DummyDeviceRepository dummyDeviceRepository,
            @Value("${pre.enroll.table.name:WaterMeterDevicePreEnrollments}")
                    String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.userService = userService;
        this.preEnrollRepository = preEnrollRepository;
        this.unitService = unitService;
        this.enrollmentCompletionService = enrollmentCompletionService;
        this.dummyDeviceRepository = dummyDeviceRepository;
        this.tableName = tableName;
    }

    DevicePreEnrollResponse preEnroll(
            String userId, String tenantId, DevicePreEnrollRequest request) {
        validateRequest(request);
        validateTenantMember(userId, tenantId);

        String serialNumber = request.serialNumber().trim();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

        DevicePreEnrollRecord record =
                new DevicePreEnrollRecord(
                        serialNumber,
                        tenantId,
                        STATUS_PENDING,
                        now.toString(),
                        expiresAt.toString(),
                        userId,
                        null);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(record.toItem())
                        .build());

        return new DevicePreEnrollResponse(
                tenantId, serialNumber, STATUS_PENDING, expiresAt.toString());
    }

    /**
     * Associates a serial with the tenant (like pre-enroll) and immediately marks it enrolled.
     * Used for dummy / test devices that skip real fleet provisioning.
     */
    DevicePreEnrollResponse dummyEnroll(
            String userId, String tenantId, DevicePreEnrollRequest request) {
        validateRequest(request);
        validateTenantMember(userId, tenantId);

        String serialNumber = request.serialNumber().trim();
        Instant now = Instant.now();
        String nowStr = now.toString();
        Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

        preEnrollRepository.save(
                new DevicePreEnrollRecord(
                        serialNumber,
                        tenantId,
                        PreEnrollRepository.STATUS_ENROLLED,
                        nowStr,
                        expiresAt.toString(),
                        userId,
                        nowStr));

        dummyDeviceRepository.register(tenantId, serialNumber, nowStr, userId);

        unitService
                .findByTenantAndDeviceId(tenantId, serialNumber)
                .ifPresent(
                        unit -> {
                            if (UnitRecord.STATUS_PENDING.equals(unit.enrollmentStatus())) {
                                enrollmentCompletionService.onEnrolled(
                                        tenantId, serialNumber, nowStr);
                            }
                        });

        return new DevicePreEnrollResponse(
                tenantId, serialNumber, PreEnrollRepository.STATUS_ENROLLED, expiresAt.toString());
    }

    public DeviceTenantLookupResponse lookupTenantBySerial(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "serialNumber is required");
        }

        DevicePreEnrollRecord record =
                preEnrollRepository
                        .findBySerialNumber(serialNumber.trim())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Serial number not found"));

        if (record.tenantId() == null || record.tenantId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Tenant not found for serial number");
        }

        return new DeviceTenantLookupResponse(record.serialNumber(), record.tenantId());
    }

    private void validateRequest(DevicePreEnrollRequest request) {
        if (request == null
                || request.serialNumber() == null
                || request.serialNumber().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "serialNumber is required");
        }
    }

    private void validateTenantMember(String userId, String tenantId) {
        UserRecord user =
                userService
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not registered"));

        if (user.tenantId() == null || user.tenantId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User is not associated with a tenant");
        }

        if (!user.tenantId().equals(tenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tenant does not match authenticated user");
        }
    }
}
