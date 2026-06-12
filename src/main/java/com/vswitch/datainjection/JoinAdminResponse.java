package com.vswitch.datainjection;

public record JoinAdminResponse(
        String tenantId, boolean onboardingComplete, boolean isTenantOwner) {}
