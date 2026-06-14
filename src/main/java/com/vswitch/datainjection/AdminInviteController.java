package com.vswitch.datainjection;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminInviteController {

    private final AdminInviteService adminInviteService;

    AdminInviteController(AdminInviteService adminInviteService) {
        this.adminInviteService = adminInviteService;
    }

    @PostMapping("/tenants/{tenantId}/admin-invites")
    AdminInviteResponse createAdminInvite(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        return adminInviteService.createAdminInvite(jwt.getSubject(), tenantId);
    }

    @PostMapping("/tenants/join/admin")
    JoinAdminResponse joinAsAdmin(
            @AuthenticationPrincipal Jwt jwt, @RequestBody JoinAdminRequest request) {
        return adminInviteService.joinAsAdmin(jwt.getSubject(), request);
    }
}
