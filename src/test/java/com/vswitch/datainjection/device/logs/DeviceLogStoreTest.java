package com.vswitch.datainjection.device.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class DeviceLogStoreTest {

    @TempDir Path tempDir;

    private DeviceLogStore store;

    @BeforeEach
    void setUp() {
        store = new DeviceLogStore(tempDir.toString(), 500L, new ObjectMapper());
    }

    @Test
    void appendAssignsMonotonicSeqAndSupportsCatchUp() {
        List<DeviceLogStore.PendingLogLine> batch1 =
                List.of(
                        new DeviceLogStore.PendingLogLine("2026-06-17 10:00:00", "a"),
                        new DeviceLogStore.PendingLogLine("2026-06-17 10:00:01", "b"));
        DeviceLogAppendResult first =
                store.appendBatch("tenant-1", "DEV001", "DEV001", batch1);
        assertFalse(first.fileReset());
        assertEquals(2, first.entries().size());
        assertEquals(1L, first.entries().get(0).seq());
        assertEquals(2L, first.entries().get(1).seq());

        List<DeviceLogEntry> catchUp = store.readAfterSeq("tenant-1", "DEV001", 1);
        assertEquals(1, catchUp.size());
        assertEquals(2L, catchUp.get(0).seq());
        assertEquals("b", catchUp.get(0).message());
    }

    @Test
    void resetsOversizedFileBeforeAppend() throws Exception {
        Path logFile =
                tempDir.resolve("tenants").resolve("tenant-1").resolve("devices").resolve("DEV001.ndjson");
        Path metaFile = logFile.resolveSibling("DEV001.ndjson.meta");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "x".repeat(600), StandardCharsets.UTF_8);
        new ObjectMapper().writeValue(metaFile.toFile(), java.util.Map.of("lastSeq", 99));

        DeviceLogAppendResult result =
                store.appendBatch(
                        "tenant-1",
                        "DEV001",
                        "DEV001",
                        List.of(new DeviceLogStore.PendingLogLine("ts", "fresh")));

        assertTrue(result.fileReset());
        assertEquals(1, result.entries().size());
        assertEquals(1L, result.entries().get(0).seq());
        assertTrue(Files.size(logFile) < 500L);
    }

    @Test
    void resolveLogFileReturnsEmptyWhenMissing() {
        assertTrue(store.resolveLogFile("tenant-1", "MISSING").isEmpty());
    }
}
