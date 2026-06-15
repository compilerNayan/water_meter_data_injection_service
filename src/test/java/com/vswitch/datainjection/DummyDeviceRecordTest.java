package com.vswitch.datainjection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DummyDeviceRecordTest {

    @Test
    void deviceKeyCombinesTenantAndNormalizedSerial() {
        assertEquals(
                "tenant-1#WM000001",
                DummyDeviceRecord.deviceKeyFor("tenant-1", "wm000001"));
    }

    @Test
    void createNormalizesSerial() {
        DummyDeviceRecord record =
                DummyDeviceRecord.create("tenant-1", "wm000001", "2026-01-01T00:00:00Z", "user-1");

        assertEquals("tenant-1#WM000001", record.deviceKey());
        assertEquals("WM000001", record.serialNumber());
        assertEquals("tenant-1", record.tenantId());
    }
}
