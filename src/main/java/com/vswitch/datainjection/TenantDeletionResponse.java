package com.vswitch.datainjection;

public record TenantDeletionResponse(
        String tenantId,
        int unitsDeleted,
        int deviceDataSetsDeleted,
        int preEnrollmentsDeleted,
        int dummyDevicesDeleted,
        int usersDeleted,
        int cognitoUsersDeleted,
        boolean tenantDeleted) {}
