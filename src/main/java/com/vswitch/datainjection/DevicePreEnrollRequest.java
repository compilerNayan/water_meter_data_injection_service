package com.vswitch.datainjection;

public record DevicePreEnrollRequest(
        String serialNumber, String block, String wing, String floor) {

    public DevicePreEnrollRequest(String serialNumber) {
        this(serialNumber, null, null, null);
    }
}
