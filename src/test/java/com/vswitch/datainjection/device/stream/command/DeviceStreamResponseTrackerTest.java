package com.vswitch.datainjection.device.stream.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vswitch.datainjection.device.DeviceMqttHttpResponse;

class DeviceStreamResponseTrackerTest {

    private DeviceStreamResponseTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DeviceStreamResponseTracker();
    }

    @Test
    void completesPendingFutureByRequestId() throws Exception {
        CompletableFuture<DeviceMqttHttpResponse> pending = tracker.beginAwaitingResponse("req-1");
        tracker.completeResponse(
                "req-1", new DeviceMqttHttpResponse(200, java.util.Map.of("enrolled", true)));

        DeviceMqttHttpResponse response = pending.get();
        assertEquals(200, response.statusCode());
        assertEquals(true, response.body().get("enrolled"));
        assertFalse(tracker.hasPending("req-1"));
    }

    @Test
    void ignoresUnknownRequestId() {
        tracker.completeResponse("missing", new DeviceMqttHttpResponse(200, java.util.Map.of()));
        assertEquals(0, tracker.pendingCount());
    }
}
