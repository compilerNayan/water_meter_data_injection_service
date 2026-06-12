package com.vswitch.datainjection;

public record TenantMetadataDeviceEntry(
        String unitId,
        String name,
        String deviceId,
        String flatNumber,
        String floor,
        String block,
        String wing,
        String residentName,
        String phoneNumber,
        String notes,
        String enrollmentStatus,
        boolean maintenanceMode,
        String maintenanceStartedAt,
        String unitInviteCode) {}
