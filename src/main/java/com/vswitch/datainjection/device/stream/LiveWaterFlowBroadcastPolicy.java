package com.vswitch.datainjection.device.stream;

import java.time.Duration;
import java.time.Instant;

public final class LiveWaterFlowBroadcastPolicy {

    /** Ignore live pushes when the pulse timestamp is this old or older. */
    public static final Duration MAX_PULSE_AGE = Duration.ofSeconds(10);

    /** Wait briefly so a TCP burst coalesces to the newest pulse. */
    public static final Duration COALESCE_WINDOW = Duration.ofMillis(50);

    /** At most one live push per device per interval. */
    public static final Duration MIN_BROADCAST_INTERVAL = Duration.ofSeconds(1);

    private LiveWaterFlowBroadcastPolicy() {}

    static boolean isPulseTooOld(Instant pulseTs, Instant now) {
        if (pulseTs == null) {
            return true;
        }
        return !pulseTs.isAfter(now.minus(MAX_PULSE_AGE));
    }

    static PendingWaterFlow keepLatest(PendingWaterFlow current, PendingWaterFlow incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming.payload().ts().isAfter(current.payload().ts())) {
            return incoming;
        }
        if (incoming.payload().ts().equals(current.payload().ts())
                && incoming.payload().cumulativeLiters() >= current.payload().cumulativeLiters()) {
            return incoming;
        }
        return current;
    }
}
