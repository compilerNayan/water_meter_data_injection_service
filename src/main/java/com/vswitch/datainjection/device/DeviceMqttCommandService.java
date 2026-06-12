package com.vswitch.datainjection.device;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceMqttCommandService {

    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(15);

    private final DeviceMqttClient mqttClient;
    private final DeviceMqttResponseTracker responseTracker;

    DeviceMqttCommandService(DeviceMqttClient mqttClient, DeviceMqttResponseTracker responseTracker) {
        this.mqttClient = mqttClient;
        this.responseTracker = responseTracker;
    }

    /**
     * Publishes an HTTP request string to {@code .../command} and waits for the matching
     * {@code .../status} response (one in-flight command per device).
     */
    public DeviceMqttHttpResponse sendHttpCommandAndAwaitResponse(
            String tenantId, String deviceId, String httpRequest) {
        return sendHttpCommandAndAwaitResponse(
                tenantId, deviceId, httpRequest, DEFAULT_RESPONSE_TIMEOUT);
    }

    public DeviceMqttHttpResponse sendHttpCommandAndAwaitResponse(
            String tenantId, String deviceId, String httpRequest, Duration timeout) {
        String commandTopic = DeviceMqttTopics.commandTopic(tenantId, deviceId);
        String statusTopic = DeviceMqttTopics.statusTopic(tenantId, deviceId);

        CompletableFuture<DeviceMqttHttpResponse> pending =
                responseTracker.beginAwaitingResponse(tenantId, deviceId);
        mqttClient.ensureSubscribed(statusTopic);
        mqttClient.publish(commandTopic, httpRequest);

        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            responseTracker.cancel(tenantId, deviceId);
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "Device did not respond on MQTT status topic");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseTracker.cancel(tenantId, deviceId);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while waiting for device response");
        } catch (Exception e) {
            responseTracker.cancel(tenantId, deviceId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed waiting for device MQTT response", e);
        }
    }

    public void publishHttpCommand(String tenantId, String deviceId, String httpRequest) {
        mqttClient.publish(DeviceMqttTopics.commandTopic(tenantId, deviceId), httpRequest);
    }

    public static String buildHttpGet(String path) {
        return "GET " + path + " HTTP/1.1\r\nHost: device\r\nConnection: close\r\n\r\n";
    }

    public static String buildHttpPut(String path, String jsonBody) {
        return "PUT "
                + path
                + " HTTP/1.1\r\nHost: device\r\nContent-Type: application/json\r\nContent-Length: "
                + jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + "\r\nConnection: close\r\n\r\n"
                + jsonBody;
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
