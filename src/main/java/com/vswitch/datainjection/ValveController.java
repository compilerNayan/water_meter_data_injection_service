package com.vswitch.datainjection;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vswitch.datainjection.device.DeviceMqttHttpResponse;
import com.vswitch.datainjection.device.DeviceValveService;

@RestController
@RequestMapping("/api/tenants/{tenantId}/devices/{deviceSerial}/valve")
public class ValveController {

    private final DeviceValveService deviceValveService;

    ValveController(DeviceValveService deviceValveService) {
        this.deviceValveService = deviceValveService;
    }

    @GetMapping
    public ResponseEntity<ValveApiResponse> getValve(
            @PathVariable String tenantId, @PathVariable String deviceSerial) {
        DeviceMqttHttpResponse response = deviceValveService.getValveState(tenantId, deviceSerial);
        return ResponseEntity.status(response.statusCode())
                .body(DeviceValveService.toApiResponse(response));
    }

    @PutMapping
    public ResponseEntity<ValveApiResponse> setValve(
            @PathVariable String tenantId,
            @PathVariable String deviceSerial,
            @RequestBody ValveSetRequest request) {
        if (request.pressurePercent() == null) {
            return ResponseEntity.badRequest().build();
        }
        DeviceMqttHttpResponse response =
                deviceValveService.setValveState(tenantId, deviceSerial, request);
        return ResponseEntity.status(response.statusCode())
                .body(DeviceValveService.toApiResponse(response));
    }
}
