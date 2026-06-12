package com.vswitch.datainjection;

public record UsageSummaryResponse(
        double totalVolumeLiters,
        double averagePerBucketLiters,
        PeakBucketResponse peakBucket,
        double previousPeriodTotalLiters,
        double deltaPercent) {}
