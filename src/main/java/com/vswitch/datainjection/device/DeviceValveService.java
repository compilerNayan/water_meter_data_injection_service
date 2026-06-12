package com.vswitch.datainjection.device;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.ValveApiResponse;
import com.vswitch.datainjection.ValveSetRequest;

@Service
public class DeviceValveService {

    /** Device firmware {@code ValveController} is mounted at {@code /valve}. */
    static final String VALVE_PATH = "/valve";

    private final DeviceMqttCommandService mqttCommandService;
    private final ObjectMapper objectMapper;

    DeviceValveService(DeviceMqttCommandService mqttCommandService, ObjectMapper objectMapper) {
        this.mqttCommandService = mqttCommandService;
        this.objectMapper = objectMapper;
    }

    public DeviceMqttHttpResponse getValveState(String tenantId, String deviceSerial) {
        String httpRequest = DeviceMqttCommandService.buildHttpGet(VALVE_PATH);
        return mqttCommandService.sendHttpCommandAndAwaitResponse(tenantId, deviceSerial, httpRequest);
    }

    public DeviceMqttHttpResponse setValveState(
            String tenantId, String deviceSerial, ValveSetRequest request) {
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid valve request", e);
        }
        String httpRequest = DeviceMqttCommandService.buildHttpPut(VALVE_PATH, jsonBody);
        return mqttCommandService.sendHttpCommandAndAwaitResponse(tenantId, deviceSerial, httpRequest);
    }

    public static ValveApiResponse toApiResponse(DeviceMqttHttpResponse response) {
        return new ValveApiResponse(
                readInt(response.body(), "targetPressurePercent"),
                readInt(response.body(), "actualPressurePercent"));
    }

    private static Integer readInt(java.util.Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
