package com.vswitch.datainjection.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.device.stream.DeviceStreamIngestionService;
import com.vswitch.datainjection.live.TenantLiveUpdateBroadcaster;

@ExtendWith(MockitoExtension.class)
class ThirtyMinuteBucketIngestionServiceTest {

    @Mock private DeviceFacade deviceFacade;
    @Mock private DeviceStreamIngestionService deviceStreamIngestionService;
    @Mock private TenantLiveUpdateBroadcaster liveUpdateBroadcaster;

    private ThirtyMinuteBucketIngestionService service;

    @BeforeEach
    void setUp() {
        service =
                new ThirtyMinuteBucketIngestionService(
                        deviceFacade, deviceStreamIngestionService, liveUpdateBroadcaster);
    }

    @Test
    void ingestsBucketAndClearsLiveTelemetry() {
        service.ingestMap(
                java.util.Map.of(
                        "tenantId",
                        "tenant-abc",
                        "deviceId",
                        "WM001",
                        "periodStart",
                        "2026-06-09T10:00:00Z",
                        "minutes",
                        java.util.List.of(),
                        "cumulativeLiters",
                        12.5,
                        "valveTargetPercent",
                        80));

        verify(deviceFacade).ingest30MinuteBucket(any(ThirtyMinuteBucketPayload.class));
        verify(deviceStreamIngestionService).clearLiveTelemetry("WM001");
        verify(liveUpdateBroadcaster).broadcast(eq("tenant-abc"), any());
    }
}
