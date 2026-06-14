package com.vswitch.datainjection.device.stream.handler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.DevicePreEnrollService;
import com.vswitch.datainjection.DeviceTenantLookupResponse;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamSession;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelopeParser;

@ExtendWith(MockitoExtension.class)
class EnrollmentRequestStreamHandlerTest {

    @Mock private DevicePreEnrollService preEnrollService;
    @Mock private DeviceStreamCommandService commandService;

    private EnrollmentRequestStreamHandler handler;
    private DeviceStreamEnvelopeParser envelopeParser;
    private DeviceStreamSession session;

    @BeforeEach
    void setUp() throws Exception {
        handler =
                new EnrollmentRequestStreamHandler(
                        preEnrollService, commandService, new ObjectMapper());
        envelopeParser = new DeviceStreamEnvelopeParser(new ObjectMapper());
        session = new DeviceStreamSession(new StringWriter());
    }

    @Test
    void callsNotifyEndpointWhenTenantIsFound() {
        DeviceStreamEnvelope envelope = parseEnrollmentEnvelope("WM001");
        when(preEnrollService.lookupTenantBySerial("WM001"))
                .thenReturn(new DeviceTenantLookupResponse("WM001", "tenant-abc"));

        handler.processEnrollment(envelope, session);

        ArgumentCaptor<String> httpCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandService).sendHttpDownlinkOnSession(eq(session), httpCaptor.capture());
        String http = httpCaptor.getValue();
        assertTrue(http.startsWith("POST /deviceenrollment/notify HTTP/1.1\r\n"));
        assertTrue(http.contains("\"tenantId\":\"tenant-abc\""));
        assertTrue(http.contains("\"serialNumber\":\"WM001\""));
        verify(commandService, never())
                .sendHttpDownlinkOnSession(
                        eq(session), org.mockito.ArgumentMatchers.contains("/failure"));
    }

    @Test
    void callsFailureEndpointWhenTenantIsNotFound() {
        DeviceStreamEnvelope envelope = parseEnrollmentEnvelope("WM404");
        when(preEnrollService.lookupTenantBySerial("WM404"))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Serial number not found"));

        handler.processEnrollment(envelope, session);

        ArgumentCaptor<String> httpCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandService).sendHttpDownlinkOnSession(eq(session), httpCaptor.capture());
        String http = httpCaptor.getValue();
        assertTrue(http.startsWith("POST /deviceenrollment/failure HTTP/1.1\r\n"));
        assertTrue(http.contains("\"serialNumber\":\"WM404\""));
        assertTrue(http.contains("\"code\":\"TENANT_NOT_FOUND\""));
        verify(preEnrollService).lookupTenantBySerial("WM404");
    }

    @Test
    void ignoresEnrollmentWithoutSerialNumber() {
        DeviceStreamEnvelope envelope =
                envelopeParser.parseEnvelope(
                        """
                        {"v":1,"category":"enrollment_request","tenantId":"","data":{"deviceType":"water_meter"}}
                        """);

        handler.processEnrollment(envelope, session);

        verify(preEnrollService, never()).lookupTenantBySerial(any());
        verify(commandService, never()).sendHttpDownlinkOnSession(any(), any());
    }

    private DeviceStreamEnvelope parseEnrollmentEnvelope(String serialNumber) {
        return envelopeParser.parseEnvelope(
                """
                {"v":1,"category":"enrollment_request","tenantId":"","serialNumber":"%s","data":{"serialNumber":"%s","deviceType":"water_meter"}}
                """
                        .formatted(serialNumber, serialNumber));
    }
}
