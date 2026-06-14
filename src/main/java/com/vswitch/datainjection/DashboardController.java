package com.vswitch.datainjection;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/v2/tenants/{tenantId}/dashboard")
    DashboardResponse getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "UTC") String timezone) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return dashboardService.getDashboard(tenantId, timezone);
    }
}
