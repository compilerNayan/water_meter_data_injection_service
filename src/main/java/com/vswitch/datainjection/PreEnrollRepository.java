package com.vswitch.datainjection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Repository
public class PreEnrollRepository {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ENROLLED = "enrolled";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    PreEnrollRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${pre.enroll.table.name:WaterMeterDevicePreEnrollments}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    Optional<DevicePreEnrollRecord> findBySerialNumber(String serialNumber) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "serialNumber",
                                                AttributeValue.builder().s(serialNumber).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DevicePreEnrollRecord.fromItem(response.item()));
    }

    void save(DevicePreEnrollRecord record) {
        dynamoDbClient.putItem(
                PutItemRequest.builder().tableName(tableName).item(record.toItem()).build());
    }

    List<DevicePreEnrollRecord> listByTenant(String tenantId) {
        List<DevicePreEnrollRecord> records = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            ScanRequest.Builder builder =
                    ScanRequest.builder()
                            .tableName(tableName)
                            .filterExpression("tenantId = :tenantId")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":tenantId",
                                            AttributeValue.builder().s(tenantId).build()));
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            var response = dynamoDbClient.scan(builder.build());
            for (Map<String, AttributeValue> item : response.items()) {
                records.add(DevicePreEnrollRecord.fromItem(item));
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
        return records;
    }

    void delete(String serialNumber) {
        dynamoDbClient.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(
                                Map.of(
                                        "serialNumber",
                                        AttributeValue.builder().s(serialNumber).build()))
                        .build());
    }

    int deleteAllForTenant(String tenantId) {
        int deleted = 0;
        for (DevicePreEnrollRecord record : listByTenant(tenantId)) {
            delete(record.serialNumber());
            deleted++;
        }
        return deleted;
    }
}
