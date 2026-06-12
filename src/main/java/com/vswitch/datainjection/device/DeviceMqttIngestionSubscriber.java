package com.vswitch.datainjection.device;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Subscribes to device MQTT telemetry on startup and feeds {@link IotMqttIngestionService}.
 * Replaces IoT topic-rule → Lambda forwarding when rules are not configured.
 */
@Component
@Lazy(false)
@ConditionalOnProperty(name = "device.mqtt.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceMqttIngestionSubscriber implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceMqttIngestionSubscriber.class);

    private final DeviceMqttClient mqttClient;
    private final IotMqttIngestionService ingestionService;

    DeviceMqttIngestionSubscriber(
            DeviceMqttClient mqttClient, IotMqttIngestionService ingestionService) {
        this.mqttClient = mqttClient;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        mqttClient.setInboundMessageHandler(ingestionService::handleMqttMessage);
        mqttClient.ensureSubscribed(DeviceMqttTopics.ALL_WATER_METERS_SUBSCRIBE_FILTER);
        log.info(
                "MQTT ingestion subscriber active on {}",
                DeviceMqttTopics.ALL_WATER_METERS_SUBSCRIBE_FILTER);
    }
}
