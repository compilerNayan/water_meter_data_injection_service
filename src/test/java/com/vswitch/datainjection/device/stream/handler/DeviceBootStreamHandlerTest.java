package com.vswitch.datainjection.device.stream.handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vswitch.datainjection.device.presence.PresenceHistoryService;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

@ExtendWith(MockitoExtension.class)
class DeviceBootStreamHandlerTest {

    @Mock private PresenceHistoryService presenceHistoryService;

    private DeviceBootStreamHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeviceBootStreamHandler(presenceHistoryService);
    }

    @Test
    void handleRecordsBootFromEnvelope() {
        ObjectNode data = new ObjectMapper().createObjectNode();
        DeviceStreamEnvelope envelope =
                new DeviceStreamEnvelope(1, "device_boot", "tenant-1", "WM001", data);

        handler.handle(envelope);

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(presenceHistoryService)
                .recordBoot(org.mockito.ArgumentMatchers.eq("tenant-1"), org.mockito.ArgumentMatchers.eq("WM001"), instantCaptor.capture());
        verifyNoMoreInteractions(presenceHistoryService);
    }

    @Test
    void handleIgnoresMissingTenantId() {
        ObjectNode data = new ObjectMapper().createObjectNode();
        DeviceStreamEnvelope envelope = new DeviceStreamEnvelope(1, "device_boot", "", "WM001", data);

        handler.handle(envelope);

        verifyNoMoreInteractions(presenceHistoryService);
    }
}
