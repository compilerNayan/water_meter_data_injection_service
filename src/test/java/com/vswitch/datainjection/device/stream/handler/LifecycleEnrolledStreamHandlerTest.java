package com.vswitch.datainjection.device.stream.handler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.EnrollmentCompletionService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelopeParser;

import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class LifecycleEnrolledStreamHandlerTest {

    @Mock private EnrollmentCompletionService enrollmentCompletionService;

    private LifecycleEnrolledStreamHandler handler;
    private DeviceStreamEnvelopeParser envelopeParser;

    @BeforeEach
    void setUp() {
        handler = new LifecycleEnrolledStreamHandler(enrollmentCompletionService);
        envelopeParser = new DeviceStreamEnvelopeParser(new ObjectMapper());
    }

    @Test
    void completesEnrollmentFromStreamEnvelope() {
        DeviceStreamEnvelope envelope = parseLifecycleEnrolledEnvelope("QJPDXN094", "4op3wb5");

        handler.handle(envelope);

        verify(enrollmentCompletionService)
                .onEnrolled(eq("4op3wb5"), eq("QJPDXN094"), eq("2026-06-14T12:00:00Z"));
    }

    @Test
    void usesEnvelopeLevelTenantAndSerialWhenDataOmitsThem() {
        DeviceStreamEnvelope envelope =
                envelopeParser.parseEnvelope(
                        """
                        {"v":1,"category":"lifecycle_enrolled","tenantId":"tenant-abc","serialNumber":"WM001","data":{"enrolledAt":"2026-06-14T12:00:00Z"}}
                        """);

        handler.handle(envelope);

        verify(enrollmentCompletionService)
                .onEnrolled(eq("tenant-abc"), eq("WM001"), eq("2026-06-14T12:00:00Z"));
    }

    @Test
    void ignoresEnvelopeWithoutTenantId() {
        DeviceStreamEnvelope envelope =
                envelopeParser.parseEnvelope(
                        """
                        {"v":1,"category":"lifecycle_enrolled","serialNumber":"WM001","data":{"deviceId":"WM001"}}
                        """);

        handler.handle(envelope);

        verify(enrollmentCompletionService, never()).onEnrolled(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void logsAndContinuesWhenCompletionServiceRejectsEnrollment() {
        DeviceStreamEnvelope envelope = parseLifecycleEnrolledEnvelope("WM404", "tenant-x");
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Unit not found"))
                .when(enrollmentCompletionService)
                .onEnrolled("tenant-x", "WM404", "2026-06-14T12:00:00Z");

        handler.handle(envelope);

        verify(enrollmentCompletionService)
                .onEnrolled(eq("tenant-x"), eq("WM404"), eq("2026-06-14T12:00:00Z"));
    }

    private DeviceStreamEnvelope parseLifecycleEnrolledEnvelope(
            String serialNumber, String tenantId) {
        return envelopeParser.parseEnvelope(
                """
                {"v":1,"category":"lifecycle_enrolled","tenantId":"%s","serialNumber":"%s","data":{"tenantId":"%s","deviceId":"%s","serialNumber":"%s","enrolledAt":"2026-06-14T12:00:00Z"}}
                """
                        .formatted(tenantId, serialNumber, tenantId, serialNumber, serialNumber));
    }
}
