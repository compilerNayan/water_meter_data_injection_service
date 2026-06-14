package com.vswitch.datainjection;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantMetadataController {

    private final TenantMetadataService tenantMetadataService;
    private final UserService userService;

    TenantMetadataController(
            TenantMetadataService tenantMetadataService, UserService userService) {
        this.tenantMetadataService = tenantMetadataService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/metadata/hash")
    TenantMetadataHashResponse getMetadataHash(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return tenantMetadataService.getHash(tenantId);
    }

    @GetMapping("/v2/tenants/{tenantId}/metadata")
    TenantMetadataResponse getMetadata(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return tenantMetadataService.getMetadata(tenantId);
    }
}
