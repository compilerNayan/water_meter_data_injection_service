package com.vswitch.datainjection.device.stream;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.ValveApiResponse;
import com.vswitch.datainjection.ValveSetRequest;
import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.DeviceValveService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;

/**
 * Calls device {@code ValveController} ({@code GET/PUT /valve}) over the TCP stream using
 * curl-style HTTP embedded in {@code server_message} downlink.
 *
 * <p>TODO: restore TCP downlink once device connectivity is verified — stub responses are
 * returned for laptop curl testing.
 */
@Service
public class DeviceStreamValveService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamValveService.class);

    /** Device firmware {@code ValveController} is mounted at {@code /valve}. */
    static final String VALVE_PATH = "/valve";

    private static final int DUMMY_TARGET_PRESSURE_PERCENT = 85;
    private static final int DUMMY_ACTUAL_PRESSURE_PERCENT = 82;

    private final DeviceStreamCommandService commandService;
    private final ObjectMapper objectMapper;

    DeviceStreamValveService(DeviceStreamCommandService commandService, ObjectMapper objectMapper) {
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    public DeviceMqttHttpResponse getValveState(String deviceSerial) {
        log.info("Valve GET stub response for serial={}", deviceSerial);
        return dummyGetResponse(deviceSerial);

        /*
        String httpRequest = DeviceStreamHttpRequestBuilder.buildGet(VALVE_PATH);
        DeviceMqttHttpResponse response =
                commandService.sendHttpCommandAndAwaitResponse(deviceSerial, httpRequest);
        log.info(
                "Valve GET response for serial={}: status={} body={}",
                deviceSerial,
                response.statusCode(),
                response.body());
        return response;
        */
    }

    public DeviceMqttHttpResponse setValveState(String deviceSerial, ValveSetRequest request) {
        if (request == null || request.pressurePercent() == null) {
            throw new IllegalArgumentException("pressurePercent is required");
        }

        log.info(
                "Valve PUT stub response for serial={} pressurePercent={}",
                deviceSerial,
                request.pressurePercent());
        return dummyPutResponse(deviceSerial, request);

        /*
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid valve request", e);
        }

        String httpRequest = DeviceStreamHttpRequestBuilder.buildPut(VALVE_PATH, jsonBody);
        DeviceMqttHttpResponse response =
                commandService.sendHttpCommandAndAwaitResponse(deviceSerial, httpRequest);
        log.info(
                "Valve PUT response for serial={} pressurePercent={}: status={} body={}",
                deviceSerial,
                request.pressurePercent(),
                response.statusCode(),
                response.body());
        return response;
        */
    }

    private static DeviceMqttHttpResponse dummyGetResponse(String deviceSerial) {
        return new DeviceMqttHttpResponse(
                200,
                Map.of(
                        "targetPressurePercent", DUMMY_TARGET_PRESSURE_PERCENT,
                        "actualPressurePercent", DUMMY_ACTUAL_PRESSURE_PERCENT,
                        "deviceSerial", deviceSerial,
                        "stub", true));
    }

    private static DeviceMqttHttpResponse dummyPutResponse(
            String deviceSerial, ValveSetRequest request) {
        int pressure = request.pressurePercent();
        return new DeviceMqttHttpResponse(
                200,
                Map.of(
                        "targetPressurePercent", pressure,
                        "actualPressurePercent", pressure,
                        "deviceSerial", deviceSerial,
                        "stub", true));
    }

    public ValveApiResponse getValveApiResponse(String deviceSerial) {
        return DeviceValveService.toApiResponse(getValveState(deviceSerial));
    }

    public ValveApiResponse setValveApiResponse(String deviceSerial, ValveSetRequest request) {
        return DeviceValveService.toApiResponse(setValveState(deviceSerial, request));
    }
}
