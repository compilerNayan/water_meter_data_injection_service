package com.vswitch.datainjection.device;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.vswitch.datainjection.DayHistoryRecord;
import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.MockDeviceProfile;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@Repository
public class DeviceStore {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DynamoDbClient dynamoDbClient;
    private final String deviceStateTable;
    private final String todaySlotsTable;
    private final String dayHistoryTable;
    private final String deviceConfigTable;

    public DeviceStore(
            DynamoDbClient dynamoDbClient,
            @Value("${device.state.table.name:WaterMeterDeviceState}") String deviceStateTable,
            @Value("${today.slots.table.name:WaterMeterTodaySlots}") String todaySlotsTable,
            @Value("${day.history.table.name:WaterMeterDayHistory}") String dayHistoryTable,
            @Value("${device.config.table.name:WaterMeterDeviceConfig}") String deviceConfigTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.deviceStateTable = deviceStateTable;
        this.todaySlotsTable = todaySlotsTable;
        this.dayHistoryTable = dayHistoryTable;
        this.deviceConfigTable = deviceConfigTable;
    }

    public Optional<DeviceStateRecord> findDeviceState(String deviceId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(deviceStateTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DeviceStateRecord.fromItem(response.item()));
    }

    void putDeviceState(DeviceStateRecord state) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceStateTable)
                        .item(state.toItem())
                        .build());
    }

    public Optional<DeviceConfigRecord> findDeviceConfig(String deviceId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(deviceConfigTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DeviceConfigRecord.fromItem(response.item()));
    }

    void putDeviceConfig(DeviceConfigRecord config) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(deviceConfigTable)
                        .item(config.toItem())
                        .build());
    }

    public void putTodaySlot(TodaySlotRecord record) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(todaySlotsTable)
                        .item(record.toItem())
                        .build());
    }

    public boolean hasTodaySlots(String deviceId, LocalDate localDate) {
        return !queryTodaySlotsByLocalDate(deviceId, formatDate(localDate)).isEmpty();
    }

    public List<TodaySlotRecord> queryTodaySlotsByLocalDate(String deviceId, String localDate) {
        List<TodaySlotRecord> records = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(todaySlotsTable)
                            .keyConditionExpression("deviceId = :deviceId")
                            .filterExpression("localDate = :localDate")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                            AttributeValue.builder().s(deviceId).build(),
                                            ":localDate",
                                            AttributeValue.builder().s(localDate).build()));

            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }

            var response = dynamoDbClient.query(builder.build());
            for (var item : response.items()) {
                records.add(TodaySlotRecord.fromItem(item));
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

        return records;
    }

    public void deleteTodaySlotsForLocalDate(String deviceId, String localDate) {
        for (TodaySlotRecord slot : queryTodaySlotsByLocalDate(deviceId, localDate)) {
            dynamoDbClient.deleteItem(
                    DeleteItemRequest.builder()
                            .tableName(todaySlotsTable)
                            .key(
                                    Map.of(
                                            "deviceId",
                                            AttributeValue.builder().s(deviceId).build(),
                                            "slotKey",
                                            AttributeValue.builder().s(slot.slotKey()).build()))
                            .build());
        }
    }

    public void putDayHistory(DayHistoryRecord record) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(dayHistoryTable)
                        .item(record.toItem())
                        .build());
    }

    public Optional<DayHistoryRecord> findDayHistory(String deviceId, LocalDate date) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(dayHistoryTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build(),
                                                "dayKey",
                                                AttributeValue.builder()
                                                        .s(DayHistoryRecord.dayKeyFor(date))
                                                        .build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DayHistoryRecord.fromItem(response.item()));
    }

    public List<DayHistoryRecord> queryDayHistory(String deviceId, LocalDate from, LocalDate to) {
        String fromKey = DayHistoryRecord.dayKeyFor(from);
        String toKey = DayHistoryRecord.dayKeyFor(to);

        List<DayHistoryRecord> records = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(dayHistoryTable)
                            .keyConditionExpression(
                                    "deviceId = :deviceId AND dayKey BETWEEN :fromKey AND :toKey")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                            AttributeValue.builder().s(deviceId).build(),
                                            ":fromKey",
                                            AttributeValue.builder().s(fromKey).build(),
                                            ":toKey",
                                            AttributeValue.builder().s(toKey).build()));

            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }

            var response = dynamoDbClient.query(builder.build());
            for (var item : response.items()) {
                records.add(DayHistoryRecord.fromItem(item));
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

        return records;
    }

    public void deleteAllDeviceData(String deviceId) {
        deleteDeviceState(deviceId);
        deleteDeviceConfig(deviceId);
        deleteAllTodaySlots(deviceId);
        deleteAllDayHistory(deviceId);
    }

    void deleteDeviceState(String deviceId) {
        dynamoDbClient.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(deviceStateTable)
                        .key(
                                Map.of(
                                        "deviceId",
                                        AttributeValue.builder().s(deviceId).build()))
                        .build());
    }

    void deleteDeviceConfig(String deviceId) {
        dynamoDbClient.deleteItem(
                DeleteItemRequest.builder()
                        .tableName(deviceConfigTable)
                        .key(
                                Map.of(
                                        "deviceId",
                                        AttributeValue.builder().s(deviceId).build()))
                        .build());
    }

    void deleteAllTodaySlots(String deviceId) {
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(todaySlotsTable)
                            .keyConditionExpression("deviceId = :deviceId")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                            AttributeValue.builder().s(deviceId).build()));
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            var response = dynamoDbClient.query(builder.build());
            for (var item : response.items()) {
                TodaySlotRecord slot = TodaySlotRecord.fromItem(item);
                dynamoDbClient.deleteItem(
                        DeleteItemRequest.builder()
                                .tableName(todaySlotsTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build(),
                                                "slotKey",
                                                AttributeValue.builder().s(slot.slotKey()).build()))
                                .build());
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
    }

    void deleteAllDayHistory(String deviceId) {
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder =
                    QueryRequest.builder()
                            .tableName(dayHistoryTable)
                            .keyConditionExpression("deviceId = :deviceId")
                            .expressionAttributeValues(
                                    Map.of(
                                            ":deviceId",
                                            AttributeValue.builder().s(deviceId).build()));
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            var response = dynamoDbClient.query(builder.build());
            for (var item : response.items()) {
                DayHistoryRecord day = DayHistoryRecord.fromItem(item);
                dynamoDbClient.deleteItem(
                        DeleteItemRequest.builder()
                                .tableName(dayHistoryTable)
                                .key(
                                        Map.of(
                                                "deviceId",
                                                AttributeValue.builder().s(deviceId).build(),
                                                "dayKey",
                                                AttributeValue.builder().s(day.dayKey()).build()))
                                .build());
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
    }

    void applyHistoricalCumulative(String deviceId, double additionalLiters, Instant lastHour) {
        DeviceStateRecord current =
                findDeviceState(deviceId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Device state missing for " + deviceId));

        String timestamp = lastHour.toString();
        DeviceStateRecord updated =
                new DeviceStateRecord(
                        deviceId,
                        current.tenantId(),
                        current.cumulativeLiters() + additionalLiters,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        current.valveTargetPercent(),
                        current.valveActualPercent(),
                        current.lastUserPressurePercent(),
                        timestamp,
                        current.mockProfile(),
                        timestamp);

        putDeviceState(updated);
    }

    static String mockProfileName(String deviceId) {
        int seed = Math.abs(deviceId.hashCode());
        int mod = seed % 100;
        if (mod < 8) {
            return MockDeviceProfile.AnomalyType.LEAK_BURST.name();
        }
        if (mod < 12) {
            return MockDeviceProfile.AnomalyType.VALVE_MISMATCH.name();
        }
        if (mod < 18) {
            return MockDeviceProfile.AnomalyType.OFFLINE.name();
        }
        return MockDeviceProfile.AnomalyType.NORMAL.name();
    }

    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMAT);
    }
}
