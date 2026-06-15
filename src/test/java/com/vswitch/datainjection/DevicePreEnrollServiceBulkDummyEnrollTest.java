package com.vswitch.datainjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vswitch.datainjection.dummy.DummyDeviceHistoricalBackfillService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePreEnrollServiceBulkDummyEnrollTest {

    @Mock private software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamoDbClient;
    @Mock private UserService userService;
    @Mock private PreEnrollRepository preEnrollRepository;
    @Mock private UnitService unitService;
    @Mock private EnrollmentCompletionService enrollmentCompletionService;
    @Mock private DummyDeviceRepository dummyDeviceRepository;
    @Mock private DummyDeviceHistoricalBackfillService dummyHistoricalBackfillService;
    @Mock private ExecutorService dummyBulkEnrollExecutor;

    private DevicePreEnrollService service;

    @BeforeEach
    void setUp() {
        service =
                new DevicePreEnrollService(
                        dynamoDbClient,
                        userService,
                        preEnrollRepository,
                        unitService,
                        enrollmentCompletionService,
                        dummyDeviceRepository,
                        dummyHistoricalBackfillService,
                        dummyBulkEnrollExecutor,
                        "WaterMeterDevicePreEnrollments",
                        1000);
        lenient()
                .doAnswer(
                        invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(dummyBulkEnrollExecutor)
                .execute(any(Runnable.class));
        lenient()
                .when(userService.findById("user-1"))
                .thenReturn(
                        Optional.of(
                                new UserRecord(
                                        "user-1",
                                        "a@test.com",
                                        "",
                                        "A",
                                        "B",
                                        "A B",
                                        "tenant-1",
                                        true,
                                        false,
                                        Instant.now().toString(),
                                        Instant.now().toString())));
        lenient()
                .when(unitService.findByTenantAndDeviceId(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void bulkDummyEnrollProcessesEachDeviceAndSchedulesBackfill() {
        BulkDummyEnrollResponse response =
                service.bulkDummyEnroll(
                        "user-1",
                        "tenant-1",
                        new BulkDummyEnrollRequest(
                                List.of(
                                        new DevicePreEnrollRequest("WM001", "A", "East", "1"),
                                        new DevicePreEnrollRequest("WM002", "B", "West", "2"))));

        assertEquals("tenant-1", response.tenantId());
        assertEquals(2, response.requested());
        assertEquals(2, response.enrolled());
        assertEquals(0, response.failed());
        assertEquals(2, response.results().size());

        verify(dummyDeviceRepository, times(2))
                .register(eq("tenant-1"), anyString(), anyString(), eq("user-1"));
        verify(dummyHistoricalBackfillService).scheduleBackfill("tenant-1", "WM001");
        verify(dummyHistoricalBackfillService).scheduleBackfill("tenant-1", "WM002");
        verify(unitService).upsertDummyUnitLocation("tenant-1", "WM001", "A", "East", "1");
        verify(unitService).upsertDummyUnitLocation("tenant-1", "WM002", "B", "West", "2");
    }

    @Test
    void bulkDummyEnrollRejectsMoreThanMaxDevices() {
        var devices =
                java.util.stream.IntStream.range(0, 1001)
                        .mapToObj(i -> new DevicePreEnrollRequest("WM" + i))
                        .toList();

        var ex =
                assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () ->
                                service.bulkDummyEnroll(
                                        "user-1",
                                        "tenant-1",
                                        new BulkDummyEnrollRequest(devices)));

        assertEquals(400, ex.getStatusCode().value());
        verify(dummyDeviceRepository, never())
                .register(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void bulkDummyEnrollRejectsDuplicateSerialNumbers() {
        var ex =
                assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () ->
                                service.bulkDummyEnroll(
                                        "user-1",
                                        "tenant-1",
                                        new BulkDummyEnrollRequest(
                                                List.of(
                                                        new DevicePreEnrollRequest("WM001"),
                                                        new DevicePreEnrollRequest("wm001")))));

        assertEquals(400, ex.getStatusCode().value());
    }
}
