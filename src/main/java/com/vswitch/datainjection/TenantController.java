package com.vswitch.datainjection;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantController {

    private final TenantService tenantService;
    private final UserService userService;

    TenantController(TenantService tenantService, UserService userService) {
        this.tenantService = tenantService;
        this.userService = userService;
    }

    @GetMapping("/tenants/{tenantId}")
    TenantResponse getTenant(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String tenantId) {
        userService.requireTenantMember(jwt.getSubject(), tenantId);
        return tenantService.getTenant(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/building")
    ResponseEntity<TenantResponse> createBuilding(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestBody CreateBuildingRequest request) {
        userService.requireTenantOwner(jwt.getSubject(), tenantId);
        TenantResponse response =
                tenantService.updateBuilding(
                        tenantId, request.name(), normalizeStructure(request.structure()));
        userService.completeOnboarding(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/tenants/{tenantId}/structure")
    TenantResponse updateStructure(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestBody UpdateStructureRequest request) {
        userService.requireTenantOwner(jwt.getSubject(), tenantId);
        return tenantService.updateStructure(tenantId, normalizeStructure(request.structure()));
    }

    private StructureDto normalizeStructure(StructureDto structure) {
        if (structure == null || structure.blocks() == null) {
            return new StructureDto(java.util.List.of());
        }
        return structure;
    }
}
