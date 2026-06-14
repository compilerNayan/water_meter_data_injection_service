package com.vswitch.datainjection;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildingStatsController {

    private final BuildingStatsService buildingStatsService;
    private final UserService userService;

    BuildingStatsController(BuildingStatsService buildingStatsService, UserService userService) {
        this.buildingStatsService = buildingStatsService;
        this.userService = userService;
    }

    @GetMapping("/tenants/{tenantId}/building/summary")
    BuildingSummaryResponse getSummary(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return buildingStatsService.getSummary(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/building/rankings")
    BuildingRankingsResponse getRankings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(defaultValue = "overall") String groupBy,
            @RequestParam(required = false) String blockId,
            @RequestParam(defaultValue = "5") int limit) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return buildingStatsService.getRankings(tenantId, period, groupBy, blockId, limit);
    }
}
