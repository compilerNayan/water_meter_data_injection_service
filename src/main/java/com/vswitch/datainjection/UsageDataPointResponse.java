package com.vswitch.datainjection;

public record UsageDataPointResponse(
        String timestamp, double volumeLiters, double avgFlowRateLpm) {}
