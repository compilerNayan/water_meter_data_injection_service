package com.vswitch.datainjection.device.stream.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.device.ThirtyMinuteBucketIngestionService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelopeParser;

@ExtendWith(MockitoExtension.class)
class WaterThirtyMinuteStreamHandlerTest {

    @Mock private ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService;

    private WaterThirtyMinuteStreamHandler handler;
    private DeviceStreamEnvelopeParser envelopeParser;

    @BeforeEach
    void setUp() {
        handler = new WaterThirtyMinuteStreamHandler(thirtyMinuteBucketIngestionService);
        envelopeParser = new DeviceStreamEnvelopeParser(new ObjectMapper());
    }

    @Test
    void ingestsThirtyMinuteBucketFromStreamEnvelope() {
        DeviceStreamEnvelope envelope =
                envelopeParser.parseEnvelope(
                        """
                        {"v":1,"category":"water_30m","tenantId":"tenant-abc","serialNumber":"WM001","data":{"tenantId":"tenant-abc","deviceId":"WM001","periodStart":"2026-06-09T10:00:00Z","minutes":[{"t":"2026-06-09T10:00:00Z","ml":45}],"cumulativeLiters":12.5,"valveTargetPercent":80}}
                        """);

        handler.handle(envelope);

        verify(thirtyMinuteBucketIngestionService)
                .ingestStreamEnvelope(eq("tenant-abc"), eq("WM001"), any());
    }
}
