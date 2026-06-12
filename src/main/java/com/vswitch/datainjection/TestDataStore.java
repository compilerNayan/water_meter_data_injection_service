package com.vswitch.datainjection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.Optional;

@Service
public class TestDataStore {

    private static final String KEY_PREFIX = "test-data:";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    TestDataStore(
            DynamoDbClient dynamoDbClient,
            @Value("${watermeter.users-table:WaterMeterUsers}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    void save(String key, String value) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(
                                Map.of(
                                        "userId",
                                                AttributeValue.builder()
                                                        .s(KEY_PREFIX + key)
                                                        .build(),
                                        "displayName",
                                                AttributeValue.builder().s(value).build()))
                        .build());
    }

    Optional<String> find(String key) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "userId",
                                                AttributeValue.builder()
                                                        .s(KEY_PREFIX + key)
                                                        .build()))
                                .build());
        if (!response.hasItem()) {
            return Optional.empty();
        }
        AttributeValue displayName = response.item().get("displayName");
        if (displayName == null || displayName.s() == null) {
            return Optional.empty();
        }
        return Optional.of(displayName.s());
    }
}
