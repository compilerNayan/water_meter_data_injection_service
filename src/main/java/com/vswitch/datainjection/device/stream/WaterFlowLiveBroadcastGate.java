package com.vswitch.datainjection.device.stream;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.vswitch.datainjection.live.LiveUpdateMessage;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import jakarta.annotation.PreDestroy;

@Component
public class WaterFlowLiveBroadcastGate {

    private static final Logger log = LoggerFactory.getLogger(WaterFlowLiveBroadcastGate.class);

    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PendingWaterFlow> pendingByDevice = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastBroadcastAt = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFlush = new ConcurrentHashMap<>();

    WaterFlowLiveBroadcastGate(
            TenantLiveUpdateBroadcaster liveUpdateBroadcaster,
            Clock clock,
            ScheduledExecutorService waterFlowBroadcastScheduler) {
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
        this.clock = clock;
        this.scheduler = waterFlowBroadcastScheduler;
    }

    public void offer(DeviceStreamPulsePayload payload, double flowRateLpm) {
        Instant now = clock.instant();
        if (LiveWaterFlowBroadcastPolicy.isPulseTooOld(payload.ts(), now)) {
            return;
        }

        String key = DeviceLiveTelemetryStore.normalizeDeviceId(payload.deviceId());
        PendingWaterFlow incoming = new PendingWaterFlow(payload, flowRateLpm);
        pendingByDevice.merge(key, incoming, LiveWaterFlowBroadcastPolicy::keepLatest);
        scheduleFlush(key, now);
    }

    void flushNowForTests(String deviceId) {
        flush(DeviceLiveTelemetryStore.normalizeDeviceId(deviceId));
    }

    @PreDestroy
    void shutdown() {
        for (ScheduledFuture<?> future : scheduledFlush.values()) {
            future.cancel(false);
        }
        scheduledFlush.clear();
    }

    private void scheduleFlush(String deviceKey, Instant now) {
        Instant lastBroadcast = lastBroadcastAt.get(deviceKey);
        long coalesceMs = LiveWaterFlowBroadcastPolicy.COALESCE_WINDOW.toMillis();
        long rateLimitMs = 0;
        if (lastBroadcast != null) {
            long elapsedMs = Duration.between(lastBroadcast, now).toMillis();
            long minIntervalMs = LiveWaterFlowBroadcastPolicy.MIN_BROADCAST_INTERVAL.toMillis();
            if (elapsedMs < minIntervalMs) {
                rateLimitMs = minIntervalMs - elapsedMs;
            }
        }

        long delayMs = Math.max(coalesceMs, rateLimitMs);
        ScheduledFuture<?> existing = scheduledFlush.get(deviceKey);
        if (existing != null && !existing.isDone() && !existing.isCancelled()) {
            return;
        }

        ScheduledFuture<?> future =
                scheduler.schedule(() -> flush(deviceKey), delayMs, TimeUnit.MILLISECONDS);
        scheduledFlush.put(deviceKey, future);
    }

    private void flush(String deviceKey) {
        scheduledFlush.remove(deviceKey);
        PendingWaterFlow pending = pendingByDevice.get(deviceKey);
        if (pending == null) {
            return;
        }

        Instant now = clock.instant();
        if (LiveWaterFlowBroadcastPolicy.isPulseTooOld(pending.payload().ts(), now)) {
            pendingByDevice.remove(deviceKey, pending);
            return;
        }

        Instant lastBroadcast = lastBroadcastAt.get(deviceKey);
        if (lastBroadcast != null) {
            long elapsedMs = Duration.between(lastBroadcast, now).toMillis();
            if (elapsedMs < LiveWaterFlowBroadcastPolicy.MIN_BROADCAST_INTERVAL.toMillis()) {
                scheduleFlush(deviceKey, now);
                return;
            }
        }

        pendingByDevice.remove(deviceKey, pending);
        lastBroadcastAt.put(deviceKey, now);
        broadcast(pending);
    }

    private void broadcast(PendingWaterFlow pending) {
        DeviceStreamPulsePayload payload = pending.payload();
        try {
            liveUpdateBroadcaster.broadcast(
                    payload.tenantId(),
                    LiveUpdateMessage.waterFlow(
                            payload.tenantId(),
                            payload.deviceId(),
                            unitIdFor(payload.deviceId()),
                            payload.ts(),
                            payload.ml(),
                            pending.flowRateLpm(),
                            payload.cumulativeLiters()));
        } catch (Exception e) {
            log.warn(
                    "Failed to broadcast stream water_flow for {}/{}",
                    payload.tenantId(),
                    payload.deviceId(),
                    e);
        }
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }
}

record PendingWaterFlow(DeviceStreamPulsePayload payload, double flowRateLpm) {}
