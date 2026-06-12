package com.vswitch.datainjection;

import java.util.List;

public record TenantMetadataResponse(
        String metadataHash,
        String tenantId,
        String buildingName,
        StructureDto structure,
        TenantMetadataOwnerEntry owner,
        List<TenantMetadataDeviceEntry> devices) {}
