package com.vswitch.datainjection.device.stream.command;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.DeviceMqttHttpResponseParser;

/**
 * Generic REST-over-TCP client: sends curl-style HTTP requests to a connected device via
 * {@code server_message} downlink and waits for matching {@code device_message} uplink.
 */
@Service
public class DeviceStreamCommandService {

    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(15);

    private final DeviceStreamConnectionRegistry connectionRegistry;
    private final DeviceStreamResponseTracker responseTracker;
    private final ObjectMapper objectMapper;

    DeviceStreamCommandService(
            DeviceStreamConnectionRegistry connectionRegistry,
            DeviceStreamResponseTracker responseTracker,
            ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.responseTracker = responseTracker;
        this.objectMapper = objectMapper;
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public DeviceMqttHttpResponse sendHttpCommandAndAwaitResponse(
            String serialNumber, String httpRequest) {
        return sendHttpCommandAndAwaitResponse(
                serialNumber, httpRequest, DEFAULT_RESPONSE_TIMEOUT);
    }

    public DeviceMqttHttpResponse sendHttpCommandAndAwaitResponse(
            String serialNumber, String httpRequest, Duration timeout) {
        String requestId = newRequestId();
        return sendHttpCommandAndAwaitResponse(serialNumber, requestId, httpRequest, timeout);
    }

    public DeviceMqttHttpResponse sendHttpCommandAndAwaitResponse(
            String serialNumber, String requestId, String httpRequest, Duration timeout) {
        CompletableFuture<DeviceMqttHttpResponse> pending =
                responseTracker.beginAwaitingResponse(requestId);
        sendDownlink(serialNumber, requestId, httpRequest);

        return awaitResponse(requestId, pending, timeout);
    }

    public DeviceMqttHttpResponse sendHttpCommandOnSession(
            DeviceStreamSession session, String httpRequest) {
        return sendHttpCommandOnSession(session, httpRequest, DEFAULT_RESPONSE_TIMEOUT);
    }

    public DeviceMqttHttpResponse sendHttpCommandOnSession(
            DeviceStreamSession session, String httpRequest, Duration timeout) {
        String requestId = newRequestId();
        CompletableFuture<DeviceMqttHttpResponse> pending =
                responseTracker.beginAwaitingResponse(requestId);
        sendDownlink(session, requestId, httpRequest);
        return awaitResponse(requestId, pending, timeout);
    }

    private DeviceMqttHttpResponse awaitResponse(
            String requestId,
            CompletableFuture<DeviceMqttHttpResponse> pending,
            Duration timeout) {
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            responseTracker.cancel(requestId);
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Device did not respond for requestId=" + requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseTracker.cancel(requestId);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Interrupted while waiting for device response");
        } catch (Exception e) {
            responseTracker.cancel(requestId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed waiting for device TCP response", e);
        }
    }

    public void sendDownlink(String serialNumber, String requestId, String httpRequest) {
        DeviceStreamSession session =
                connectionRegistry
                        .findBySerial(serialNumber)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "Device is not connected: " + serialNumber));
        sendDownlink(session, requestId, httpRequest);
    }

    public void sendDownlink(DeviceStreamSession session, String requestId, String httpRequest) {
        if (session == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Device session is not available");
        }

        String line =
                DeviceStreamDownlinkMessage.toServerMessageLine(
                        objectMapper, requestId, httpRequest);
        try {
            session.sendLine(line);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Failed to send downlink to device", e);
        }
    }

    public void completeDeviceMessage(String requestId, String payloadText) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        if (!responseTracker.hasPending(requestId)) {
            return;
        }
        DeviceMqttHttpResponse response =
                DeviceMqttHttpResponseParser.parseHttpText(payloadText, objectMapper);
        responseTracker.completeResponse(requestId, response);
    }
}
