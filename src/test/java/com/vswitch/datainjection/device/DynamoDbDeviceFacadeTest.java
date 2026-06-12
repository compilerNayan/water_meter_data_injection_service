package com.vswitch.datainjection.device;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.DeviceStateRecord;
import com.vswitch.datainjection.VolumeReadingService;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbDeviceFacadeTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbDeviceFacade facade;

    @BeforeEach
    void setUp() {
        DeviceStore store =
                new DeviceStore(
                        dynamoDbClient,
                        "WaterMeterDeviceState",
                        "WaterMeterTodaySlots",
                        "WaterMeterDayHistory",
                        "WaterMeterDeviceConfig");
        VolumeReadingService volumeReadingService = new VolumeReadingService(store);
        facade = new DynamoDbDeviceFacade(store, volumeReadingService, 72);
    }

    @Test
    void initializeDeviceStateStartsAtZeroCumulative() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        facade.initializeDeviceState("WM000001", "k3m9x2a");

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        var item = putCaptor.getValue().item();
        assertEquals(
                0.0,
                Double.parseDouble(item.get("cumulativeLiters").n()),
                0.001);
    }

    @Test
    void ingestSecondPulseUpdatesFlowRate() {
        String now = Instant.now().toString();
        DeviceStateRecord existing =
                new DeviceStateRecord(
                        "WM000001",
                        "k3m9x2a",
                        100,
                        0,
                        DeviceStateRecord.STATUS_IDLE,
                        100,
                        100,
                        100,
                        now,
                        "",
                        now);
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(existing.toItem()).build());

        facade.ingestSecondPulse("k3m9x2a", "WM000001", Instant.parse(now), 1000);

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        assertEquals(
                DeviceStateRecord.STATUS_FLOWING,
                putCaptor.getValue().item().get("status").s());
    }
}
