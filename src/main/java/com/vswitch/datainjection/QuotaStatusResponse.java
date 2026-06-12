package com.vswitch.datainjection;

public record QuotaStatusResponse(
        String date,
        double usedLiters,
        int activeStepIndex,
        Double quotaCapPercent,
        double remainingLiters,
        Double nextStepAtLiters) {}
