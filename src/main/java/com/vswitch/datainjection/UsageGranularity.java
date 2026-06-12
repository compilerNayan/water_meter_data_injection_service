package com.vswitch.datainjection;

import java.time.Duration;

enum UsageGranularity {
    M1("1m", Duration.ofMinutes(1)),
    M5("5m", Duration.ofMinutes(5)),
    M15("15m", Duration.ofMinutes(15)),
    M30("30m", Duration.ofMinutes(30)),
    H1("1h", Duration.ofHours(1)),
    D1("1d", Duration.ofDays(1));

    private final String apiValue;
    private final Duration bucketDuration;

    UsageGranularity(String apiValue, Duration bucketDuration) {
        this.apiValue = apiValue;
        this.bucketDuration = bucketDuration;
    }

    String apiValue() {
        return apiValue;
    }

    Duration bucketDuration() {
        return bucketDuration;
    }

    static UsageGranularity fromApiValue(String value) {
        for (UsageGranularity g : values()) {
            if (g.apiValue.equals(value)) {
                return g;
            }
        }
        return H1;
    }
}
