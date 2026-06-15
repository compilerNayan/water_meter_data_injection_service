package com.vswitch.datainjection.device.stream;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import com.vswitch.datainjection.live.WaterFlowTickDevice;

import jakarta.annotation.PreDestroy;

@Component
public class WaterFlowLiveBroadcastGate {

    private static final Logger log = LoggerFactory.getLogger(WaterFlowLiveBroadcastGate.class);

    private final TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ConcurrentHashMap<String, PendingWaterFlow>> pendingByTenant =
            new ConcurrentHashMap<>();
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

        String tenantId = payload.tenantId();
        String deviceKey = DeviceLiveTelemetryStore.normalizeDeviceId(payload.deviceId());
        PendingWaterFlow incoming = new PendingWaterFlow(payload, flowRateLpm);
        pendingByTenant
                .computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>())
                .merge(deviceKey, incoming, LiveWaterFlowBroadcastPolicy::keepLatest);
        scheduleTenantFlush(tenantId, now);
    }

    void flushNowForTests(String deviceId) {
        String deviceKey = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        for (Map.Entry<String, ConcurrentHashMap<String, PendingWaterFlow>> entry :
                pendingByTenant.entrySet()) {
            if (entry.getValue().containsKey(deviceKey)) {
                flushTenant(entry.getKey());
                return;
            }
        }
    }

    public void clearDevice(String deviceId) {
        String deviceKey = DeviceLiveTelemetryStore.normalizeDeviceId(deviceId);
        for (ConcurrentHashMap<String, PendingWaterFlow> pending : pendingByTenant.values()) {
            pending.remove(deviceKey);
        }
    }

    @PreDestroy
    void shutdown() {
        for (ScheduledFuture<?> future : scheduledFlush.values()) {
            future.cancel(false);
        }
        scheduledFlush.clear();
    }

    private void scheduleTenantFlush(String tenantId, Instant now) {
        ScheduledFuture<?> existing = scheduledFlush.get(tenantId);
        if (existing != null && !existing.isDone() && !existing.isCancelled()) {
            return;
        }

        long delayMs = computeDelayMs(tenantId, now);
        ScheduledFuture<?> future =
                scheduler.schedule(() -> flushTenant(tenantId), delayMs, TimeUnit.MILLISECONDS);
        scheduledFlush.put(tenantId, future);
    }

    private long computeDelayMs(String tenantId, Instant now) {
        long coalesceMs = LiveWaterFlowBroadcastPolicy.COALESCE_WINDOW.toMillis();
        Instant lastBroadcast = lastBroadcastAt.get(tenantId);
        if (lastBroadcast == null) {
            return coalesceMs;
        }
        long elapsedMs = Duration.between(lastBroadcast, now).toMillis();
        long minIntervalMs = LiveWaterFlowBroadcastPolicy.MIN_BROADCAST_INTERVAL.toMillis();
        if (elapsedMs >= minIntervalMs) {
            return coalesceMs;
        }
        return Math.max(coalesceMs, minIntervalMs - elapsedMs);
    }

    private void flushTenant(String tenantId) {
        scheduledFlush.remove(tenantId);
        ConcurrentHashMap<String, PendingWaterFlow> pending = pendingByTenant.get(tenantId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        Instant lastBroadcast = lastBroadcastAt.get(tenantId);
        if (lastBroadcast != null) {
            long elapsedMs = Duration.between(lastBroadcast, now).toMillis();
            if (elapsedMs < LiveWaterFlowBroadcastPolicy.MIN_BROADCAST_INTERVAL.toMillis()) {
                scheduleTenantFlush(tenantId, now);
                return;
            }
        }

        List<WaterFlowTickDevice> devices = new ArrayList<>();
        for (Map.Entry<String, PendingWaterFlow> entry : pending.entrySet()) {
            PendingWaterFlow pendingFlow = entry.getValue();
            if (LiveWaterFlowBroadcastPolicy.isPulseTooOld(pendingFlow.payload().ts(), now)) {
                pending.remove(entry.getKey(), pendingFlow);
                continue;
            }
            devices.add(toTickDevice(pendingFlow));
            pending.remove(entry.getKey(), pendingFlow);
        }

        if (devices.isEmpty()) {
            return;
        }

        lastBroadcastAt.put(tenantId, now);
        broadcastTick(tenantId, now, devices);
    }

    private void broadcastTick(String tenantId, Instant tickTs, List<WaterFlowTickDevice> devices) {
        try {
            liveUpdateBroadcaster.broadcast(
                    tenantId, LiveUpdateMessage.waterFlowTick(tenantId, tickTs, devices));
        } catch (Exception e) {
            log.warn("Failed to broadcast water_flow_tick for tenant {}", tenantId, e);
        }
    }

    private static WaterFlowTickDevice toTickDevice(PendingWaterFlow pending) {
        DeviceStreamPulsePayload payload = pending.payload();
        return new WaterFlowTickDevice(
                payload.deviceId(),
                unitIdFor(payload.deviceId()),
                payload.ts().toString(),
                payload.ml(),
                pending.flowRateLpm(),
                payload.cumulativeLiters(),
                payload.ml() > 0 ? "flowing" : "idle");
    }

    private static String unitIdFor(String deviceId) {
        return "wm-" + deviceId;
    }
}

record PendingWaterFlow(DeviceStreamPulsePayload payload, double flowRateLpm) {}
