package com.vswitch.datainjection.device.stream.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record DeviceStreamEnvelope(
        int version,
        String category,
        String tenantId,
        String serialNumber,
        JsonNode data) {

    public boolean hasCategory() {
        return category != null && !category.isBlank();
    }
}
