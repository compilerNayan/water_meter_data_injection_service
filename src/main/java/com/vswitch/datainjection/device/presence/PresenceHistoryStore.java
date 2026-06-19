package com.vswitch.datainjection.device.presence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@Repository
public class PresenceHistoryStore {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public PresenceHistoryStore(
            DynamoDbClient dynamoDbClient,
            @Value("${presence.events.table.name:WaterMeterDevicePresenceEvents}")
                    String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void putEvent(PresenceTransitionRecord record) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(record.toItem())
                        .build());
    }

    public List<PresenceTransitionRecord> queryEvents(
            String deviceId, String fromEventAtInclusive, String toEventAtInclusive) {
        List<PresenceTransitionRecord> records = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(tableName)
                            .keyConditionExpression(
                                    "deviceId = :deviceId AND eventAt BETWEEN :from AND :to")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                                    AttributeValue.builder().s(deviceId).build(),
                                            ":from",
                                                    AttributeValue.builder()
                                                            .s(fromEventAtInclusive)
                                                            .build(),
                                            ":to",
                                                    AttributeValue.builder()
                                                            .s(toEventAtInclusive)
                                                            .build()))
                            .scanIndexForward(true);

            if (exclusiveStartKey != null) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }

            var response = dynamoDbClient.query(builder.build());
            response.items()
                    .forEach(item -> records.add(PresenceTransitionRecord.fromItem(item)));
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

        return records;
    }

    public Optional<PresenceTransitionRecord> findLastEventBefore(
            String deviceId, String beforeEventAtExclusive) {
        var response =
                dynamoDbClient.query(
                        QueryRequest.builder()
                                .tableName(tableName)
                                .keyConditionExpression(
                                        "deviceId = :deviceId AND eventAt < :before")
                                .expressionAttributeValues(
                                        Map.of(
                                                ":deviceId",
                                                        AttributeValue.builder().s(deviceId).build(),
                                                ":before",
                                                        AttributeValue.builder()
                                                                .s(beforeEventAtExclusive)
                                                                .build()))
                                .scanIndexForward(false)
                                .limit(1)
                                .build());

        if (response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PresenceTransitionRecord.fromItem(response.items().get(0)));
    }
}
