package com.vswitch.datainjection;

public record TenantMetadataOwnerEntry(
        String userId,
        String displayName,
        String email,
        String phone,
        String firstName,
        String lastName) {}
