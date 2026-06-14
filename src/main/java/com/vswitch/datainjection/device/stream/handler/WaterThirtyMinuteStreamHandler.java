package com.vswitch.datainjection.device.stream.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.vswitch.datainjection.device.ThirtyMinuteBucketIngestionService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

@Component
public class WaterThirtyMinuteStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(WaterThirtyMinuteStreamHandler.class);

    private final ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService;

    WaterThirtyMinuteStreamHandler(
            ThirtyMinuteBucketIngestionService thirtyMinuteBucketIngestionService) {
        this.thirtyMinuteBucketIngestionService = thirtyMinuteBucketIngestionService;
    }

    public void handle(DeviceStreamEnvelope envelope) {
        try {
            thirtyMinuteBucketIngestionService.ingestStreamEnvelope(
                    envelope.tenantId(), envelope.serialNumber(), envelope.data());
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Rejected water_30m from serial={}: {}",
                    envelope.serialNumber(),
                    ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error(
                    "Failed to ingest water_30m from serial={}",
                    envelope.serialNumber(),
                    ex);
            throw new IllegalArgumentException("Invalid water_30m envelope data", ex);
        }
    }
}
