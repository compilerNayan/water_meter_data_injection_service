package com.vswitch.datainjection.live;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaterFlowTickDevice(
        String deviceId,
        String unitId,
        String ts,
        double ml,
        double flowRateLpm,
        double cumulativeLiters,
        Double todayLiters,
        String status) {}
