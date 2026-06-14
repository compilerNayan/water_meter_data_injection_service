package com.vswitch.datainjection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public lookup used by AWS IoT fleet provisioning to resolve tenant for a device serial. */
@RestController
public class DeviceTenantLookupController {

    private final DevicePreEnrollService devicePreEnrollService;

    DeviceTenantLookupController(DevicePreEnrollService devicePreEnrollService) {
        this.devicePreEnrollService = devicePreEnrollService;
    }

    @GetMapping("/devices/{serialNumber}/tenant")
    DeviceTenantLookupResponse lookupTenant(@PathVariable String serialNumber) {
        return devicePreEnrollService.lookupTenantBySerial(serialNumber);
    }
}
