package com.vswitch.datainjection.device;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Tracks a single in-flight MQTT command per device. The device publishes its HTTP response on
 * {@code /status}; ingestion treats that as the response to the last command sent on {@code /command}.
 */
@Component
public class DeviceMqttResponseTracker {

    private final ConcurrentHashMap<String, CompletableFuture<DeviceMqttHttpResponse>> pending =
            new ConcurrentHashMap<>();

    public CompletableFuture<DeviceMqttHttpResponse> beginAwaitingResponse(
            String tenantId, String deviceId) {
        String key = deviceKey(tenantId, deviceId);
        CompletableFuture<DeviceMqttHttpResponse> future = new CompletableFuture<>();
        pending.put(key, future);
        return future;
    }

    public void completeResponse(String tenantId, String deviceId, DeviceMqttHttpResponse response) {
        String key = deviceKey(tenantId, deviceId);
        CompletableFuture<DeviceMqttHttpResponse> future = pending.remove(key);
        if (future != null) {
            future.complete(response);
        }
    }

    public Optional<CompletableFuture<DeviceMqttHttpResponse>> getPending(
            String tenantId, String deviceId) {
        return Optional.ofNullable(pending.get(deviceKey(tenantId, deviceId)));
    }

    public void cancel(String tenantId, String deviceId) {
        CompletableFuture<DeviceMqttHttpResponse> future =
                pending.remove(deviceKey(tenantId, deviceId));
        if (future != null) {
            future.cancel(true);
        }
    }

    private static String deviceKey(String tenantId, String deviceId) {
        return tenantId.trim() + ":" + deviceId.trim();
    }
}
