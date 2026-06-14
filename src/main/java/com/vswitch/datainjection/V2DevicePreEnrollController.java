package com.vswitch.datainjection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class V2DevicePreEnrollController {

    private final DevicePreEnrollService devicePreEnrollService;

    V2DevicePreEnrollController(DevicePreEnrollService devicePreEnrollService) {
        this.devicePreEnrollService = devicePreEnrollService;
    }

    @PostMapping("/v2/tenants/{tenantId}/devices/pre-enroll")
    ResponseEntity<DevicePreEnrollResponse> preEnrollDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestBody DevicePreEnrollRequest request) {
        DevicePreEnrollResponse response =
                devicePreEnrollService.preEnroll(jwt.getSubject(), tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
