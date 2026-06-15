package com.vswitch.datainjection;

import java.util.ArrayList;
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
public class DummyDeviceRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    DummyDeviceRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${dummy.devices.table.name:WaterMeterDummyDevices}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    void register(String tenantId, String serialNumber, String createdAt, String createdByUserId) {
        DummyDeviceRecord record =
                DummyDeviceRecord.create(tenantId, serialNumber, createdAt, createdByUserId);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(record.toItem())
                        .build());
    }

    boolean isDummy(String tenantId, String serialNumber) {
        return find(tenantId, serialNumber).isPresent();
    }

    Optional<DummyDeviceRecord> find(String tenantId, String serialNumber) {
        String deviceKey = DummyDeviceRecord.deviceKeyFor(tenantId, serialNumber);
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "deviceKey",
                                                AttributeValue.builder().s(deviceKey).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DummyDeviceRecord.fromItem(response.item()));
    }

    public List<DummyDeviceRecord> listAll() {
        var response = dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        List<DummyDeviceRecord> devices = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            devices.add(DummyDeviceRecord.fromItem(item));
        }
        return devices;
    }

    List<DummyDeviceRecord> listByTenant(String tenantId) {
        List<DummyDeviceRecord> devices = new ArrayList<>();
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
                devices.add(DummyDeviceRecord.fromItem(item));
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
        return devices;
    }

    void delete(String deviceKey) {
        dynamoDbClient.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("deviceKey", AttributeValue.builder().s(deviceKey).build()))
                        .build());
    }

    int deleteAllForTenant(String tenantId) {
        int deleted = 0;
        for (DummyDeviceRecord record : listByTenant(tenantId)) {
            delete(record.deviceKey());
            deleted++;
        }
        return deleted;
    }
}
