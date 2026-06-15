package com.vswitch.datainjection;

import java.util.List;

public record BulkDummyEnrollRequest(List<DevicePreEnrollRequest> devices) {}
