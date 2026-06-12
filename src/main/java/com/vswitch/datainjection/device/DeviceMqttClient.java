package com.vswitch.datainjection.device;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeviceMqttClient implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(DeviceMqttClient.class);
    private static final int QOS = 1;

    private final Object connectionLock = new Object();

    private final Set<String> desiredSubscriptions = ConcurrentHashMap.newKeySet();
    private volatile BiConsumer<String, byte[]> inboundMessageHandler;
    private MqttClient client;

    void setInboundMessageHandler(BiConsumer<String, byte[]> handler) {
        this.inboundMessageHandler = handler;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    void publish(String topic, String payload) {
        ensureConnected();
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            message.setQos(QOS);
            client.publish(topic, message);
            log.debug("Published MQTT message to {}", topic);
        } catch (MqttException e) {
            throw new IllegalStateException("Failed to publish MQTT message to " + topic, e);
        }
    }

    void ensureSubscribed(String topicFilter) {
        desiredSubscriptions.add(topicFilter);
        ensureConnected();
        subscribeTopicFilter(topicFilter);
    }

    private void subscribeTopicFilter(String topicFilter) {
        try {
            client.subscribe(topicFilter, QOS);
            log.info("Subscribed to MQTT topic filter {}", topicFilter);
        } catch (MqttException e) {
            throw new IllegalStateException("Failed to subscribe to MQTT topic filter " + topicFilter, e);
        }
    }

    private void resubscribeAll() {
        for (String topicFilter : desiredSubscriptions) {
            subscribeTopicFilter(topicFilter);
        }
    }

    private void ensureConnected() {
        synchronized (connectionLock) {
            if (client != null && client.isConnected()) {
                return;
            }
            connect();
        }
    }

    private void connect() {
        try {
            if (client != null) {
                try {
                    client.disconnectForcibly();
                } catch (Exception ignored) {
                    // reconnecting
                }
                try {
                    client.close();
                } catch (Exception ignored) {
                    // reconnecting
                }
            }

            String brokerHost = brokerHost();
            String clientId = "water-meter-backend-" + UUID.randomUUID();
            client = new MqttClient("ssl://" + brokerHost + ":8883", clientId, new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setSocketFactory(sslSocketFactory());
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            client.connect(options);
            resubscribeAll();
            log.info("Connected to AWS IoT MQTT broker {}", brokerHost);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to AWS IoT MQTT broker", e);
        }
    }

    private static String brokerHost() {
        String endpoint = IotMqttCredentials.MQTT_ENDPOINT;
        String withoutScheme =
                endpoint.startsWith("mqtts://")
                        ? endpoint.substring("mqtts://".length())
                        : endpoint.startsWith("ssl://")
                                ? endpoint.substring("ssl://".length())
                                : endpoint;
        int slash = withoutScheme.indexOf('/');
        return slash >= 0 ? withoutScheme.substring(0, slash) : withoutScheme;
    }

    private static SSLSocketFactory sslSocketFactory() throws Exception {
        return IotMqttSslContexts.fromPem(
                        IotMqttCredentials.CA_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_PRIVATE_KEY_PEM)
                .getSocketFactory();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost; will resubscribe on reconnect", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        BiConsumer<String, byte[]> handler = inboundMessageHandler;
        if (handler == null) {
            log.debug("Ignoring MQTT message on {} (no inbound handler registered)", topic);
            return;
        }
        try {
            handler.accept(topic, message.getPayload());
        } catch (Exception e) {
            log.error("Failed to process MQTT message on {}", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op
    }
}
