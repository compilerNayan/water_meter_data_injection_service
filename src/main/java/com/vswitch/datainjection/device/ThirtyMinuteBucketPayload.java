package com.vswitch.datainjection.device;

import java.time.Instant;
import java.util.List;

public record ThirtyMinuteBucketPayload(
        String tenantId,
        String deviceId,
        Instant periodStart,
        List<MinuteBucketEntry> minutes,
        double cumulativeLiters,
        double valveTargetPercent) {}
