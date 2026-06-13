package com.vswitch.datainjection.device;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.EnrollmentCompletionService;
import com.vswitch.datainjection.device.stream.DeviceStreamIngestionService;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IotMqttIngestionServiceTest {

    @Mock private DeviceFacade deviceFacade;
    @Mock private EnrollmentCompletionService enrollmentCompletionService;
    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;
    @Mock private DeviceStreamIngestionService deviceStreamIngestionService;

    private DeviceMqttResponseTracker responseTracker;
    private IotMqttIngestionService service;

    @BeforeEach
    void setUp() {
        responseTracker = new DeviceMqttResponseTracker();
        service =
                new IotMqttIngestionService(
                        deviceFacade,
                        enrollmentCompletionService,
                        responseTracker,
                        liveUpdateBroadcaster,
                        deviceStreamIngestionService,
                        new ObjectMapper());
    }

    @Test
    void routesLifecycleEnrolled() {
        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "k3m9x2a/water_meter/WM000001/lifecycle/enrolled",
                        "tenantId",
                        "k3m9x2a",
                        "deviceId",
                        "WM000001",
                        "serialNumber",
                        "WM000001",
                        "enrolledAt",
                        "2026-06-09T10:00:00Z"));

        verify(enrollmentCompletionService)
                .onEnrolled(eq("k3m9x2a"), eq("WM000001"), eq("2026-06-09T10:00:00Z"));
    }

    @Test
    void ignoresMqttWater1sPulse() {
        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "k3m9x2a/water_meter/WM000001/water/1s",
                        "ts",
                        "2026-06-09T10:30:05Z",
                        "ml",
                        45));

        verify(deviceFacade, never()).ingestSecondPulse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble());
        verify(liveUpdateBroadcaster, never())
                .broadcast(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void thirtyMinuteBucketClearsStreamStateAndBroadcastsRefresh() {
        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "k3m9x2a/water_meter/WM000001/water/30m",
                        "tenantId",
                        "k3m9x2a",
                        "deviceId",
                        "WM000001",
                        "periodStart",
                        "2026-06-09T10:00:00Z",
                        "minutes",
                        java.util.List.of(),
                        "cumulativeLiters",
                        12.5,
                        "valveTargetPercent",
                        100));

        verify(deviceStreamIngestionService).clearLiveTelemetry("WM000001");
        verify(liveUpdateBroadcaster)
                .broadcast(eq("k3m9x2a"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ingestsFromRawMqttMessageIgnoresWater1s() {
        service.handleMqttMessage(
                "k3m9x2a/water_meter/WM000001/water/1s",
                "{\"ts\":\"2026-06-09T10:30:05Z\",\"ml\":45}".getBytes());

        verify(deviceFacade, never()).ingestSecondPulse(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void routesStatusResponseToPendingCommandAndValveIngest() {
        var pending =
                responseTracker.beginAwaitingResponse("k3m9x2a", "WM000001");

        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "k3m9x2a/water_meter/WM000001/status",
                        "payload",
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                                + "{\"targetPressurePercent\":80,\"actualPressurePercent\":78}"));

        verify(deviceFacade).ingestValveStateReport("k3m9x2a", "WM000001", 80.0, 78.0);

        DeviceMqttHttpResponse httpResponse = pending.join();
        org.junit.jupiter.api.Assertions.assertEquals(200, httpResponse.statusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                80, ((Number) httpResponse.body().get("targetPressurePercent")).intValue());
    }
}
