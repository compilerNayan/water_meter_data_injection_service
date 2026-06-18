package com.vswitch.datainjection.device.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.live.DeviceLogBroadcaster;
import com.vswitch.datainjection.live.TenantLiveSessionRegistry;

class DeviceLogStreamHandlerTest {

    @TempDir java.nio.file.Path tempDir;

    private DeviceLogStore logStore;
    private DeviceLogStreamHandler handler;

    @BeforeEach
    void setUp() {
        logStore = new DeviceLogStore(tempDir.toString(), 1024L * 1024L, new ObjectMapper());
        DeviceLogBroadcaster broadcaster =
                new DeviceLogBroadcaster(
                        new TenantLiveSessionRegistry(), new ObjectMapper(), false);
        handler = new DeviceLogStreamHandler(logStore, broadcaster);
    }

    @Test
    void parsesFirmwareLogArrayAndAppendsWithSeq() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode data = mapper.createArrayNode();
        data.addObject().put("2026-06-17 16:31:12", "pulse=1");
        data.addObject().put("2026-06-17 16:31:13", "pulse=2");

        DeviceStreamEnvelope envelope =
                new DeviceStreamEnvelope(1, "log", "tenant-1", "WM000001", data);

        handler.handle(envelope);

        List<DeviceLogEntry> stored = logStore.readAfterSeq("tenant-1", "WM000001", 0);
        assertEquals(2, stored.size());
        assertEquals(1L, stored.get(0).seq());
        assertEquals("pulse=1", stored.get(0).message());
        assertEquals(2L, stored.get(1).seq());
    }

    @Test
    void ignoresEnvelopeWithoutTenant() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode data = mapper.createArrayNode();
        data.addObject().put("2026-06-17 16:31:12", "msg");

        handler.handle(new DeviceStreamEnvelope(1, "log", "", "WM000001", data));

        assertTrue(logStore.readAfterSeq("tenant-1", "WM000001", 0).isEmpty());
    }

    @Test
    void parseFirmwareLogArrayExtractsTimestampKeys() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode data = mapper.createArrayNode();
        data.addObject().put("2026-06-17 10:00:00", "hello");

        List<DeviceLogStore.PendingLogLine> lines = DeviceLogStreamHandler.parseFirmwareLogArray(data);
        assertEquals(1, lines.size());
        assertEquals("2026-06-17 10:00:00", lines.get(0).ts());
        assertEquals("hello", lines.get(0).message());
    }
}
