package com.vswitch.datainjection;

public record DailySummaryDayResponse(
        String date, double totalLiters, int peakHour, double peakHourLiters) {}
