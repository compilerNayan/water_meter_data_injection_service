package com.vswitch.datainjection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.dummy.DummyDeviceHistoricalBackfillService;
import com.vswitch.datainjection.dummy.DummyDeviceTelemetrySimulator;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class DevicePreEnrollService {

    private static final String STATUS_PENDING = "pending";
    static final int DEFAULT_MAX_BULK_DUMMY_ENROLL = 1000;

    private final DynamoDbClient dynamoDbClient;
    private final UserService userService;
    private final PreEnrollRepository preEnrollRepository;
    private final UnitService unitService;
    private final EnrollmentCompletionService enrollmentCompletionService;
    private final DummyDeviceRepository dummyDeviceRepository;
    private final DummyDeviceHistoricalBackfillService dummyHistoricalBackfillService;
    private final DummyDeviceTelemetrySimulator dummyDeviceTelemetrySimulator;
    private final ExecutorService dummyBulkEnrollExecutor;
    private final String tableName;
    private final int maxBulkDummyEnroll;

    DevicePreEnrollService(
            DynamoDbClient dynamoDbClient,
            UserService userService,
            PreEnrollRepository preEnrollRepository,
            UnitService unitService,
            EnrollmentCompletionService enrollmentCompletionService,
            DummyDeviceRepository dummyDeviceRepository,
            DummyDeviceHistoricalBackfillService dummyHistoricalBackfillService,
            @Autowired(required = false) DummyDeviceTelemetrySimulator dummyDeviceTelemetrySimulator,
            @Qualifier("dummyBulkEnrollExecutor") ExecutorService dummyBulkEnrollExecutor,
            @Value("${pre.enroll.table.name:WaterMeterDevicePreEnrollments}")
                    String tableName,
            @Value("${dummy.bulk.enroll.max:1000}") int maxBulkDummyEnroll) {
        this.dynamoDbClient = dynamoDbClient;
        this.userService = userService;
        this.preEnrollRepository = preEnrollRepository;
        this.unitService = unitService;
        this.enrollmentCompletionService = enrollmentCompletionService;
        this.dummyDeviceRepository = dummyDeviceRepository;
        this.dummyHistoricalBackfillService = dummyHistoricalBackfillService;
        this.dummyDeviceTelemetrySimulator = dummyDeviceTelemetrySimulator;
        this.dummyBulkEnrollExecutor = dummyBulkEnrollExecutor;
        this.tableName = tableName;
        this.maxBulkDummyEnroll = Math.max(1, maxBulkDummyEnroll);
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
        return enrollDummyDevice(userId, tenantId, request);
    }

    BulkDummyEnrollResponse bulkDummyEnroll(
            String userId, String tenantId, BulkDummyEnrollRequest request) {
        validateBulkRequest(request);
        validateTenantMember(userId, tenantId);

        List<DevicePreEnrollRequest> devices = request.devices();
        List<CompletableFuture<BulkDummyEnrollItemResult>> futures = new ArrayList<>();
        for (DevicePreEnrollRequest device : devices) {
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> enrollDummyDeviceSafe(userId, tenantId, device),
                            dummyBulkEnrollExecutor));
        }

        List<BulkDummyEnrollItemResult> results =
                futures.stream().map(CompletableFuture::join).toList();
        int enrolled =
                (int)
                        results.stream()
                                .filter(
                                        result ->
                                                PreEnrollRepository.STATUS_ENROLLED.equals(
                                                        result.status()))
                                .count();

        return new BulkDummyEnrollResponse(
                tenantId, devices.size(), enrolled, devices.size() - enrolled, results);
    }

    private BulkDummyEnrollItemResult enrollDummyDeviceSafe(
            String userId, String tenantId, DevicePreEnrollRequest request) {
        String serialNumber =
                request != null && request.serialNumber() != null
                        ? request.serialNumber().trim()
                        : "";
        try {
            validateRequest(request);
            DevicePreEnrollResponse response = enrollDummyDevice(userId, tenantId, request);
            return BulkDummyEnrollItemResult.enrolled(response);
        } catch (ResponseStatusException ex) {
            return BulkDummyEnrollItemResult.failed(
                    serialNumber, ex.getReason() != null ? ex.getReason() : ex.getMessage());
        } catch (Exception ex) {
            return BulkDummyEnrollItemResult.failed(
                    serialNumber, ex.getMessage() != null ? ex.getMessage() : "enrollment failed");
        }
    }

    private DevicePreEnrollResponse enrollDummyDevice(
            String userId, String tenantId, DevicePreEnrollRequest request) {
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

        if (dummyDeviceTelemetrySimulator != null) {
            dummyDeviceTelemetrySimulator.registerDevice(tenantId, serialNumber);
        }

        unitService.upsertDummyUnitDetails(tenantId, serialNumber, request);

        unitService
                .findByTenantAndDeviceId(tenantId, serialNumber)
                .ifPresent(
                        unit -> {
                            if (UnitRecord.STATUS_PENDING.equals(unit.enrollmentStatus())) {
                                enrollmentCompletionService.onEnrolled(
                                        tenantId, serialNumber, nowStr);
                            }
                        });

        dummyHistoricalBackfillService.scheduleBackfill(tenantId, serialNumber);

        return new DevicePreEnrollResponse(
                tenantId, serialNumber, PreEnrollRepository.STATUS_ENROLLED, expiresAt.toString());
    }

    private void validateBulkRequest(BulkDummyEnrollRequest request) {
        if (request == null
                || request.devices() == null
                || request.devices().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "devices array is required and must not be empty");
        }
        if (request.devices().size() > maxBulkDummyEnroll) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "devices array must not exceed " + maxBulkDummyEnroll + " items");
        }

        Set<String> seenSerials = new HashSet<>();
        for (DevicePreEnrollRequest device : request.devices()) {
            if (device == null
                    || device.serialNumber() == null
                    || device.serialNumber().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "each device must include serialNumber");
            }
            String normalized = device.serialNumber().trim().toUpperCase();
            if (!seenSerials.add(normalized)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "duplicate serialNumber in request: " + normalized);
            }
        }
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
