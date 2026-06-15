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
class UnitServiceDummyDetailsTest {

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
    void skipsWhenNoUnitDetailsProvided() {
        unitService.upsertDummyUnitDetails("tenant-1", "WM001", new DevicePreEnrollRequest("WM001"));

        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void mergesAllDetailsIntoExistingUnit() {
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

        unitService.upsertDummyUnitDetails(
                "tenant-1",
                "WM001",
                new DevicePreEnrollRequest(
                        "WM001",
                        "Home 205",
                        "205A",
                        "3",
                        "B",
                        "West",
                        "Jane Doe",
                        "+919999999999",
                        "Corner flat"));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item =
                captor.getValue().item();

        assertEquals("Home 205", item.get("name").s());
        assertEquals("205A", item.get("flatNumber").s());
        assertEquals("3", item.get("floor").s());
        assertEquals("B", item.get("block").s());
        assertEquals("West", item.get("wing").s());
        assertEquals("Jane Doe", item.get("residentName").s());
        assertEquals("+919999999999", item.get("phoneNumber").s());
        assertEquals("Corner flat", item.get("notes").s());
        assertEquals(UnitRecord.STATUS_PENDING, item.get("enrollmentStatus").s());
        verify(deviceFacade, never()).initializeDeviceState(any(), any());
        verify(tenantMetadataService).recomputeAndPersist("tenant-1");
    }

    @Test
    void createsEnrolledUnitWhenDetailsProvidedAndUnitMissing() {
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        unitService.upsertDummyUnitDetails(
                "tenant-1",
                "WM002",
                new DevicePreEnrollRequest(
                        "WM002",
                        null,
                        "502",
                        "5",
                        "C",
                        null,
                        "John Doe",
                        "+911234567890",
                        null));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item =
                captor.getValue().item();

        assertEquals("wm-WM002", item.get("unitId").s());
        assertEquals("502", item.get("name").s());
        assertEquals("502", item.get("flatNumber").s());
        assertEquals("5", item.get("floor").s());
        assertEquals("C", item.get("block").s());
        assertEquals("John Doe", item.get("residentName").s());
        assertEquals("+911234567890", item.get("phoneNumber").s());
        assertEquals(UnitRecord.STATUS_ENROLLED, item.get("enrollmentStatus").s());
        verify(deviceFacade).initializeDeviceConfig("WM002", "tenant-1");
        verify(deviceFacade).initializeDeviceState("WM002", "tenant-1");
    }
}
