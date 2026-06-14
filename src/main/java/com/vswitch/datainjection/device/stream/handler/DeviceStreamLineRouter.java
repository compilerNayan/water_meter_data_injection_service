package com.vswitch.datainjection.device.stream.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.vswitch.datainjection.device.stream.command.DeviceStreamCommandService;
import com.vswitch.datainjection.device.stream.command.DeviceStreamConnectionRegistry;
import com.vswitch.datainjection.device.stream.command.DeviceStreamSession;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;

@Component
public class DeviceStreamLineRouter {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamLineRouter.class);

    private final DeviceStreamConnectionRegistry connectionRegistry;
    private final DeviceStreamCommandService commandService;
    private final EnrollmentRequestStreamHandler enrollmentRequestHandler;

    DeviceStreamLineRouter(
            DeviceStreamConnectionRegistry connectionRegistry,
            DeviceStreamCommandService commandService,
            EnrollmentRequestStreamHandler enrollmentRequestHandler) {
        this.connectionRegistry = connectionRegistry;
        this.commandService = commandService;
        this.enrollmentRequestHandler = enrollmentRequestHandler;
    }

    public void routeEnvelope(DeviceStreamEnvelope envelope, DeviceStreamSession session) {
        bindSerial(envelope, session);

        String category = envelope.category();
        if (category == null || category.isBlank()) {
            log.debug("Ignoring envelope without category from {}", envelope.serialNumber());
            return;
        }

        switch (category) {
            case "device_message" -> handleDeviceMessage(envelope);
            case "enrollment_request" -> enrollmentRequestHandler.handle(envelope, session);
            case "water_pulse", "log", "water_30m", "lifecycle_enrolled" -> {
                // handled elsewhere or ignored for now
            }
            default -> log.debug("Unhandled stream category {} from {}", category, envelope.serialNumber());
        }
    }

    private void handleDeviceMessage(DeviceStreamEnvelope envelope) {
        JsonNode data = envelope.data();
        if (data == null || data.isNull()) {
            return;
        }
        JsonNode requestIdNode = data.get("requestId");
        JsonNode payloadNode = data.get("payload");
        if (requestIdNode == null || payloadNode == null) {
            return;
        }

        String requestId = requestIdNode.asText();
        String payload = payloadNode.isTextual() ? payloadNode.asText() : payloadNode.toString();
        commandService.completeDeviceMessage(requestId, payload);
    }

    private void bindSerial(DeviceStreamEnvelope envelope, DeviceStreamSession session) {
        if (envelope.serialNumber() != null && !envelope.serialNumber().isBlank()) {
            session.bindSerialNumber(envelope.serialNumber());
            connectionRegistry.bindSerial(session, envelope.serialNumber());
        }
    }
}
