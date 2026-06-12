package com.vswitch.datainjection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;

@Component("dynamodb")
@ConditionalOnProperty(name = "management.health.dynamodb.enabled", havingValue = "true", matchIfMissing = true)
public class DynamoDbHealthIndicator implements HealthIndicator {

    private final DynamoDbClient dynamoDbClient;
    private final String deviceStateTableName;

    DynamoDbHealthIndicator(
            DynamoDbClient dynamoDbClient,
            @Value("${watermeter.table.device-state}") String deviceStateTableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.deviceStateTableName = deviceStateTableName;
    }

    @Override
    public Health health() {
        try {
            dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName(deviceStateTableName).build());
            return Health.up().withDetail("table", deviceStateTableName).build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("table", deviceStateTableName).build();
        }
    }
}
