package com.vswitch.datainjection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${aws.region:ap-south-1}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }
}
