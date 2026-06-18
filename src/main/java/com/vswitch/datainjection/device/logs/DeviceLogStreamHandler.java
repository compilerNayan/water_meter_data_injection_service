package com.vswitch.datainjection.device.logs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.live.DeviceLogBroadcaster;

@Component
public class DeviceLogStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceLogStreamHandler.class);

    private final DeviceLogStore logStore;
    private final DeviceLogBroadcaster logBroadcaster;

    public DeviceLogStreamHandler(DeviceLogStore logStore, DeviceLogBroadcaster logBroadcaster) {
        this.logStore = logStore;
        this.logBroadcaster = logBroadcaster;
    }

    public void handle(DeviceStreamEnvelope envelope) {
        String tenantId = envelope.tenantId();
        String serialNumber = envelope.serialNumber();
        if (tenantId == null
                || tenantId.isBlank()
                || serialNumber == null
                || serialNumber.isBlank()) {
            log.debug("Ignoring log envelope without tenantId or serialNumber");
            return;
        }

        JsonNode data = envelope.data();
        if (data == null || !data.isArray()) {
            log.debug("Ignoring log envelope with non-array data from {}", serialNumber);
            return;
        }

        String deviceId = resolveDeviceId(envelope);
        List<DeviceLogStore.PendingLogLine> lines = parseFirmwareLogArray(data);
        if (lines.isEmpty()) {
            return;
        }

        DeviceLogAppendResult result =
                logStore.appendBatch(tenantId, deviceId, serialNumber, lines);
        if (result.fileReset()) {
            logBroadcaster.broadcastReset(tenantId, deviceId);
        }
        if (!result.entries().isEmpty()) {
            logBroadcaster.broadcastEntries(tenantId, result.entries());
        }
    }

    private static String resolveDeviceId(DeviceStreamEnvelope envelope) {
        JsonNode data = envelope.data();
        if (data != null && data.isObject() && data.hasNonNull("deviceId")) {
            String fromData = data.get("deviceId").asText();
            if (!fromData.isBlank()) {
                return fromData.trim().toUpperCase();
            }
        }
        return envelope.serialNumber().trim().toUpperCase();
    }

    static List<DeviceLogStore.PendingLogLine> parseFirmwareLogArray(JsonNode array) {
        List<DeviceLogStore.PendingLogLine> lines = new ArrayList<>();
        for (JsonNode element : array) {
            if (!element.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = element.fields();
            if (!fields.hasNext()) {
                continue;
            }
            Map.Entry<String, JsonNode> field = fields.next();
            String ts = field.getKey();
            String message = field.getValue().isTextual() ? field.getValue().asText() : field.getValue().toString();
            if (message.isBlank()) {
                continue;
            }
            lines.add(new DeviceLogStore.PendingLogLine(ts, message));
        }
        return lines;
    }
}
