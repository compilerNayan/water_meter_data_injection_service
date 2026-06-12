package com.vswitch.datainjection;

public record UserResponse(
        String userId,
        String email,
        String displayName,
        String phone,
        String firstName,
        String lastName,
        String tenantId,
        boolean onboardingComplete,
        boolean isTenantOwner) {}
