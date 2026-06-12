package com.vswitch.datainjection;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.vswitch.datainjection.device.DeviceMqttClient;

@Component("mqtt")
@ConditionalOnProperty(name = "device.mqtt.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConnectionHealthIndicator implements HealthIndicator {

    private final DeviceMqttClient mqttClient;

    MqttConnectionHealthIndicator(DeviceMqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Override
    public Health health() {
        if (mqttClient.isConnected()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "MQTT client not connected").build();
    }
}
