package com.vswitch.datainjection.device.stream.command;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.vswitch.datainjection.device.DeviceMqttHttpResponse;

/**
 * Maps a cloud-generated requestId to the device HTTP response received on {@code device_message}
 * uplink.
 */
@Component
public class DeviceStreamResponseTracker {

    private final ConcurrentHashMap<String, CompletableFuture<DeviceMqttHttpResponse>> pending =
            new ConcurrentHashMap<>();

    public CompletableFuture<DeviceMqttHttpResponse> beginAwaitingResponse(String requestId) {
        CompletableFuture<DeviceMqttHttpResponse> future = new CompletableFuture<>();
        pending.put(normalize(requestId), future);
        return future;
    }

    public void completeResponse(String requestId, DeviceMqttHttpResponse response) {
        CompletableFuture<DeviceMqttHttpResponse> future = pending.remove(normalize(requestId));
        if (future != null) {
            future.complete(response);
        }
    }

    public Optional<CompletableFuture<DeviceMqttHttpResponse>> getPending(String requestId) {
        return Optional.ofNullable(pending.get(normalize(requestId)));
    }

    public void cancel(String requestId) {
        CompletableFuture<DeviceMqttHttpResponse> future = pending.remove(normalize(requestId));
        if (future != null) {
            future.cancel(true);
        }
    }

    public boolean hasPending(String requestId) {
        return pending.containsKey(normalize(requestId));
    }

    public int pendingCount() {
        return pending.size();
    }

    private static String normalize(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        return requestId.trim();
    }
}
