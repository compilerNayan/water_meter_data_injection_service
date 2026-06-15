package com.vswitch.datainjection;

public record BulkDummyEnrollItemResult(
        String serialNumber, String status, String expiresAt, String error) {

    static BulkDummyEnrollItemResult enrolled(DevicePreEnrollResponse response) {
        return new BulkDummyEnrollItemResult(
                response.serialNumber(),
                PreEnrollRepository.STATUS_ENROLLED,
                response.expiresAt(),
                null);
    }

    static BulkDummyEnrollItemResult failed(String serialNumber, String error) {
        return new BulkDummyEnrollItemResult(serialNumber, "failed", null, error);
    }
}
