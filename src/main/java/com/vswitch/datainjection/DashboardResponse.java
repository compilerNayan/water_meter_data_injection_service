package com.vswitch.datainjection;

import java.util.List;

public record DashboardResponse(
        String metadataHash,
        String generatedAt,
        List<DashboardTelemetryEntry> devices) {}
