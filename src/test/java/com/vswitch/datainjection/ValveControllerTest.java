package com.vswitch.datainjection;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.DeviceValveService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValveControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DeviceValveService deviceValveService;

    @Test
    void getValveReturnsDeviceResponse() throws Exception {
        when(deviceValveService.getValveState("tenant-1", "WM000001"))
                .thenReturn(
                        new DeviceMqttHttpResponse(
                                200,
                                Map.of(
                                        "targetPressurePercent", 80,
                                        "actualPressurePercent", 78)));

        mockMvc.perform(get("/api/tenants/tenant-1/devices/WM000001/valve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPressurePercent").value(80))
                .andExpect(jsonPath("$.actualPressurePercent").value(78));

        verify(deviceValveService).getValveState("tenant-1", "WM000001");
    }

    @Test
    void putValveSendsPressurePercentToDevice() throws Exception {
        when(deviceValveService.setValveState(
                        eq("tenant-1"), eq("WM000001"), eq(new ValveSetRequest(50))))
                .thenReturn(
                        new DeviceMqttHttpResponse(
                                200,
                                Map.of(
                                        "targetPressurePercent", 50,
                                        "actualPressurePercent", 50)));

        mockMvc.perform(
                        put("/api/tenants/tenant-1/devices/WM000001/valve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pressurePercent\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPressurePercent").value(50));

        verify(deviceValveService).setValveState("tenant-1", "WM000001", new ValveSetRequest(50));
    }

    @Test
    void putValveRequiresPressurePercent() throws Exception {
        mockMvc.perform(
                        put("/api/tenants/tenant-1/devices/WM000001/valve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
