package com.vswitch.datainjection.device.stream.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** NDJSON downlink envelope understood by device {@code CloudSocket}. */
public final class DeviceStreamDownlinkMessage {

    private static final int PROTOCOL_VERSION = 1;

    private DeviceStreamDownlinkMessage() {}

    public static String toServerMessageLine(
            ObjectMapper objectMapper, String requestId, String httpRequest) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("v", PROTOCOL_VERSION);
            root.put("category", "server_message");
            root.put("requestId", requestId);
            root.put("payload", httpRequest);
            return objectMapper.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to encode server_message downlink", e);
        }
    }
}
