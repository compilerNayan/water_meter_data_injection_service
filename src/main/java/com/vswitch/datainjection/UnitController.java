package com.vswitch.datainjection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnitController {

    private final UnitService unitService;
    private final UserService userService;

    UnitController(UnitService unitService, UserService userService) {
        this.unitService = unitService;
        this.userService = userService;
    }

    @GetMapping("/tenants/{tenantId}/units")
    UnitListResponse listUnits(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return unitService.listUnits(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/units")
    ResponseEntity<UnitResponse> createUnit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestBody CreateUnitRequest request) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        UnitResponse response = unitService.createUnit(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tenants/{tenantId}/devices/{deviceId}/enrollment-status")
    EnrollmentStatusResponse getEnrollmentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return unitService.getEnrollmentStatus(tenantId, deviceId);
    }
}
