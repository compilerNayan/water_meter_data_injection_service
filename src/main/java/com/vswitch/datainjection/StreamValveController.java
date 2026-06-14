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
import com.vswitch.datainjection.device.stream.DeviceStreamValveService;

/**
 * Unauthenticated HTTP API that proxies valve GET/PUT to a connected device over the TCP stream.
 */
@RestController
@RequestMapping("/stream/devices/{deviceSerial}/valve")
public class StreamValveController {

    private final DeviceStreamValveService deviceStreamValveService;

    StreamValveController(DeviceStreamValveService deviceStreamValveService) {
        this.deviceStreamValveService = deviceStreamValveService;
    }

    @GetMapping
    public ResponseEntity<ValveApiResponse> getValve(@PathVariable String deviceSerial) {
        DeviceMqttHttpResponse response = deviceStreamValveService.getValveState(deviceSerial);
        return ResponseEntity.status(response.statusCode())
                .body(DeviceValveService.toApiResponse(response));
    }

    @PutMapping
    public ResponseEntity<ValveApiResponse> setValve(
            @PathVariable String deviceSerial, @RequestBody ValveSetRequest request) {
        if (request == null || request.pressurePercent() == null) {
            return ResponseEntity.badRequest().build();
        }
        DeviceMqttHttpResponse response =
                deviceStreamValveService.setValveState(deviceSerial, request);
        return ResponseEntity.status(response.statusCode())
                .body(DeviceValveService.toApiResponse(response));
    }
}
