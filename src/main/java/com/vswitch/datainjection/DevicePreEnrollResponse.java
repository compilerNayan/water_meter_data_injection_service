package com.vswitch.datainjection;

public record DevicePreEnrollResponse(
        String tenantId,
        String serialNumber,
        String status,
        String expiresAt) {}
