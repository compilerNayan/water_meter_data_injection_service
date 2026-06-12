package com.vswitch.datainjection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Service
public class UnitService {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final String tenantIdIndexName;
    private final TenantMetadataService tenantMetadataService;

    UnitService(
            DynamoDbClient dynamoDbClient,
            @Value("${units.table.name:WaterMeterUnits}") String tableName,
            @Autowired @Lazy TenantMetadataService tenantMetadataService) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.tenantIdIndexName = "tenantId-index";
        this.tenantMetadataService = tenantMetadataService;
    }

    UnitResponse createUnit(String tenantId, CreateUnitRequest request) {
        validateCreateRequest(request);

        String deviceId = request.deviceId().trim();
        Optional<UnitRecord> existing = findByTenantAndDeviceId(tenantId, deviceId);
        if (existing.isPresent()) {
            return existing.get().toResponse();
        }

        String now = Instant.now().toString();
        String unitId = "wm-" + deviceId;
        String inviteCode = generateInviteCode(request.flatNumber(), deviceId);

        UnitRecord unit =
                new UnitRecord(
                        unitId,
                        tenantId,
                        deviceId,
                        nullToEmpty(request.name()),
                        nullToEmpty(request.flatNumber()),
                        nullToEmpty(request.floor()),
                        nullToEmpty(request.block()),
                        nullToEmpty(request.wing()),
                        nullToEmpty(request.residentName()),
                        nullToEmpty(request.phoneNumber()),
                        nullToEmpty(request.notes()),
                        UnitRecord.STATUS_PENDING,
                        inviteCode,
                        now,
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder().tableName(tableName).item(unit.toItem()).build());

        tenantMetadataService.recomputeAndPersist(tenantId);
        return unit.toResponse();
    }

    void markEnrollmentComplete(String tenantId, String deviceId, String enrolledAt) {
        UnitRecord unit =
                findByTenantAndDeviceId(tenantId, deviceId.trim())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Unit not found"));

        String now = Instant.now().toString();
        UnitRecord updated =
                new UnitRecord(
                        unit.unitId(),
                        unit.tenantId(),
                        unit.deviceId(),
                        unit.name(),
                        unit.flatNumber(),
                        unit.floor(),
                        unit.block(),
                        unit.wing(),
                        unit.residentName(),
                        unit.phoneNumber(),
                        unit.notes(),
                        UnitRecord.STATUS_ENROLLED,
                        unit.unitInviteCode(),
                        unit.createdAt(),
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder().tableName(tableName).item(updated.toItem()).build());
    }

    List<UnitRecord> listAllUnits() {
        var response =
                dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        List<UnitRecord> units = new ArrayList<>();
        for (var item : response.items()) {
            units.add(UnitRecord.fromItem(item));
        }
        return units;
    }

    UnitListResponse listUnits(String tenantId) {
        List<UnitResponse> units =
                listUnitRecords(tenantId).stream().map(UnitRecord::toResponse).toList();
        return new UnitListResponse(units);
    }

    List<UnitRecord> listUnitRecords(String tenantId) {
        var response =
                dynamoDbClient.query(
                        QueryRequest.builder()
                                .tableName(tableName)
                                .indexName(tenantIdIndexName)
                                .keyConditionExpression("tenantId = :tenantId")
                                .expressionAttributeValues(
                                        Map.of(
                                                ":tenantId",
                                                AttributeValue.builder().s(tenantId).build()))
                                .build());

        List<UnitRecord> units = new ArrayList<>();
        for (var item : response.items()) {
            units.add(UnitRecord.fromItem(item));
        }
        return units;
    }

    EnrollmentStatusResponse getEnrollmentStatus(String tenantId, String deviceId) {
        UnitRecord unit =
                findByTenantAndDeviceId(tenantId, deviceId.trim())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Unit not found"));

        boolean enrolled = UnitRecord.STATUS_ENROLLED.equals(unit.enrollmentStatus());
        return new EnrollmentStatusResponse(enrolled, unit.enrollmentStatus());
    }

    Optional<UnitRecord> findByTenantAndDeviceId(String tenantId, String deviceId) {
        var response =
                dynamoDbClient.query(
                        QueryRequest.builder()
                                .tableName(tableName)
                                .indexName(tenantIdIndexName)
                                .keyConditionExpression("tenantId = :tenantId")
                                .filterExpression("deviceId = :deviceId")
                                .expressionAttributeValues(
                                        Map.of(
                                                ":tenantId",
                                                AttributeValue.builder().s(tenantId).build(),
                                                ":deviceId",
                                                AttributeValue.builder().s(deviceId).build()))
                                .build());

        if (response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UnitRecord.fromItem(response.items().get(0)));
    }

    Optional<UnitRecord> findById(String unitId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "unitId",
                                                AttributeValue.builder().s(unitId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UnitRecord.fromItem(response.item()));
    }

    void validateCreateRequest(CreateUnitRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body required");
        }
        if (isBlank(request.deviceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceId is required");
        }
        if (isBlank(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (isBlank(request.residentName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "residentName is required");
        }
        if (isBlank(request.phoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumber is required");
        }
    }

    private static String generateInviteCode(String flatNumber, String deviceId) {
        String base =
                flatNumber != null && !flatNumber.isBlank()
                        ? flatNumber.trim().toUpperCase().replaceAll("\\s+", "-")
                        : deviceId;
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return base + "-" + suffix;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
