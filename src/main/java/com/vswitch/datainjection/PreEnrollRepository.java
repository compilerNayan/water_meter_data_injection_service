package com.vswitch.datainjection;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

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
}
