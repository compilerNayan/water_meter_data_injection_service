package com.vswitch.datainjection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    DynamoDbClient dynamoDbClient() {
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "ap-south-1";
        }
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }
}
