package com.vswitch.datainjection;

import java.io.InputStream;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.vswitch.datainjection.device.logs.DeviceLogStore;

@RestController
public class V2DeviceLogsController {

    private final DeviceLogStore logStore;
    private final UnitService unitService;
    private final UserService userService;

    V2DeviceLogsController(
            DeviceLogStore logStore, UnitService unitService, UserService userService) {
        this.logStore = logStore;
        this.unitService = unitService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/devices/{deviceId}/logs/download")
    ResponseEntity<InputStreamResource> downloadLogs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String deviceId) {
        requireDevice(tenantId, deviceId, jwt);

        try {
            InputStream stream = logStore.openDownloadStream(tenantId, deviceId);
            String filename = deviceId.trim().toUpperCase() + "-logs.ndjson";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-ndjson"))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(new InputStreamResource(stream));
        } catch (java.io.FileNotFoundException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Log file not found");
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read log file");
        }
    }

    private void requireDevice(String tenantId, String deviceId, Jwt jwt) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        unitService
                .findByTenantAndDeviceId(tenantId, deviceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Unit not found"));
    }
}
