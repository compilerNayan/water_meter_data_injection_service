package com.vswitch.datainjection;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.device.DeviceFacade;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitServiceDummyLocationTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private TenantMetadataService tenantMetadataService;
    @Mock private PreEnrollRepository preEnrollRepository;
    @Mock private DummyDeviceRepository dummyDeviceRepository;
    @Mock private DeviceFacade deviceFacade;

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        unitService =
                new UnitService(
                        dynamoDbClient,
                        "WaterMeterUnits",
                        tenantMetadataService,
                        preEnrollRepository,
                        dummyDeviceRepository,
                        deviceFacade);
    }

    @Test
    void skipsWhenNoLocationFieldsProvided() {
        unitService.upsertDummyUnitLocation("tenant-1", "WM001", null, null, null);

        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void mergesLocationIntoExistingUnit() {
        UnitRecord existing =
                new UnitRecord(
                        "wm-WM001",
                        "tenant-1",
                        "WM001",
                        "Flat 205",
                        "205",
                        "1",
                        "A",
                        "East",
                        "Resident",
                        "+911",
                        "",
                        UnitRecord.STATUS_PENDING,
                        "205-ABCD",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of(existing.toItem())).build());

        unitService.upsertDummyUnitLocation("tenant-1", "WM001", "B", "West", "3");

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item =
                captor.getValue().item();

        assertEquals("B", item.get("block").s());
        assertEquals("West", item.get("wing").s());
        assertEquals("3", item.get("floor").s());
        assertEquals("Flat 205", item.get("name").s());
        assertEquals(UnitRecord.STATUS_PENDING, item.get("enrollmentStatus").s());
        verify(deviceFacade, never()).initializeDeviceState(any(), any());
        verify(tenantMetadataService).recomputeAndPersist("tenant-1");
    }

    @Test
    void createsEnrolledUnitWhenLocationProvidedAndUnitMissing() {
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        unitService.upsertDummyUnitLocation("tenant-1", "WM002", "C", null, "5");

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item =
                captor.getValue().item();

        assertEquals("wm-WM002", item.get("unitId").s());
        assertEquals("C", item.get("block").s());
        assertEquals("", item.get("wing").s());
        assertEquals("5", item.get("floor").s());
        assertEquals(UnitRecord.STATUS_ENROLLED, item.get("enrollmentStatus").s());
        verify(deviceFacade).initializeDeviceConfig("WM002", "tenant-1");
        verify(deviceFacade).initializeDeviceState("WM002", "tenant-1");
    }
}
