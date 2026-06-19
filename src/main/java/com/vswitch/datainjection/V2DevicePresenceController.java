package com.vswitch.datainjection;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.presence.PresenceActivityQueryService;
import com.vswitch.datainjection.device.presence.PresenceActivityQueryService.DevicePresenceActivityResponse;

@RestController
public class V2DevicePresenceController {

    private final PresenceActivityQueryService presenceActivityQueryService;
    private final UnitService unitService;
    private final UserService userService;

    V2DevicePresenceController(
            PresenceActivityQueryService presenceActivityQueryService,
            UnitService unitService,
            UserService userService) {
        this.presenceActivityQueryService = presenceActivityQueryService;
        this.unitService = unitService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/presence/activity")
    DevicePresenceActivityResponse getPresenceActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String timezone) {
        requireDevice(tenantId, deviceId, jwt);

        try {
            return presenceActivityQueryService.getActivity(
                    tenantId, deviceId, date, from, to, days, timezone);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date parameter");
        }
    }

    private void requireDevice(String tenantId, String deviceId, Jwt jwt) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        unitService
                .findByTenantAndDeviceId(tenantId, deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Unit not found"));
    }
}
