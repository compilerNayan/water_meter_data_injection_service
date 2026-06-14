package com.vswitch.datainjection.device.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void getValveStateSendsCurlStyleGetToValvePath() {
        when(commandService.sendHttpCommandAndAwaitResponse(eq("WM001"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new DeviceMqttHttpResponse(200, Map.of("targetPressurePercent", 80)));

        DeviceMqttHttpResponse response = valveService.getValveState("WM001");

        assertEquals(200, response.statusCode());
        ArgumentCaptor<String> httpCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandService).sendHttpCommandAndAwaitResponse(eq("WM001"), httpCaptor.capture());
        String http = httpCaptor.getValue();
        assertTrue(http.startsWith("GET /valve HTTP/1.1\r\n"));
        assertTrue(http.contains("Host: localhost:8080\r\n"));
        assertTrue(http.contains("User-Agent: curl/8.0.1\r\n"));
    }

    @Test
    void setValveStateSendsCurlStylePutWithPressurePercent() {
        when(commandService.sendHttpCommandAndAwaitResponse(eq("WM001"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        new DeviceMqttHttpResponse(
                                200, Map.of("targetPressurePercent", 50, "actualPressurePercent", 50)));

        DeviceMqttHttpResponse response =
                valveService.setValveState("WM001", new ValveSetRequest(50));

        assertEquals(200, response.statusCode());
        ArgumentCaptor<String> httpCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandService).sendHttpCommandAndAwaitResponse(eq("WM001"), httpCaptor.capture());
        String http = httpCaptor.getValue();
        assertTrue(http.startsWith("PUT /valve HTTP/1.1\r\n"));
        assertTrue(http.contains("Content-Type: application/json\r\n"));
        assertTrue(http.endsWith("\r\n\r\n{\"pressurePercent\":50}"));
    }
}
