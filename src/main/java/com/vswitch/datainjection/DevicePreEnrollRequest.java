package com.vswitch.datainjection;

public record DevicePreEnrollRequest(
        String serialNumber,
        String name,
        String flatNumber,
        String floor,
        String block,
        String wing,
        String residentName,
        String phoneNumber,
        String notes) {

    public DevicePreEnrollRequest(String serialNumber) {
        this(serialNumber, null, null, null, null, null, null, null, null);
    }

    public DevicePreEnrollRequest(String serialNumber, String block, String wing, String floor) {
        this(serialNumber, null, null, floor, block, wing, null, null, null);
    }

    boolean hasUnitDetails() {
        return !isBlank(name)
                || !isBlank(flatNumber)
                || !isBlank(floor)
                || !isBlank(block)
                || !isBlank(wing)
                || !isBlank(residentName)
                || !isBlank(phoneNumber)
                || !isBlank(notes);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
