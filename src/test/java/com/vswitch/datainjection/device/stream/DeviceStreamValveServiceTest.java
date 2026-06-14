package com.vswitch.datainjection.device.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.ValveSetRequest;
import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;

@ExtendWith(MockitoExtension.class)
class DeviceStreamValveServiceTest {

    @Mock private DeviceStreamCommandService commandService;

    private DeviceStreamValveService valveService;

    @BeforeEach
    void setUp() {
        valveService = new DeviceStreamValveService(commandService, new ObjectMapper());
    }

    @Test
    void getValveStateReturnsStubWithoutCallingDevice() {
        DeviceMqttHttpResponse response = valveService.getValveState("WM001");

        assertEquals(200, response.statusCode());
        assertEquals(85, response.body().get("targetPressurePercent"));
        assertEquals(82, response.body().get("actualPressurePercent"));
        assertEquals("WM001", response.body().get("deviceSerial"));
        assertEquals(true, response.body().get("stub"));
        verifyNoInteractions(commandService);
    }

    @Test
    void setValveStateReturnsStubEchoingPressurePercent() {
        DeviceMqttHttpResponse response =
                valveService.setValveState("WM001", new ValveSetRequest(50));

        assertEquals(200, response.statusCode());
        assertEquals(50, response.body().get("targetPressurePercent"));
        assertEquals(50, response.body().get("actualPressurePercent"));
        assertEquals("WM001", response.body().get("deviceSerial"));
        assertEquals(true, response.body().get("stub"));
        verifyNoInteractions(commandService);
    }
}
