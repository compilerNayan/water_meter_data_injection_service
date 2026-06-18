package com.vswitch.datainjection.device.logs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DeviceLogStore {

    private static final Logger log = LoggerFactory.getLogger(DeviceLogStore.class);
    private static final int DEFAULT_CATCH_UP_LIMIT = 5000;

    private final Path basePath;
    private final long maxFileBytes;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public DeviceLogStore(
            @Value("${device.logs.base-path:data/device-logs}") String basePath,
            @Value("${device.logs.max-file-bytes:104857600}") long maxFileBytes,
            ObjectMapper objectMapper) {
        this.basePath = Path.of(basePath);
        this.maxFileBytes = maxFileBytes;
        this.objectMapper = objectMapper;
    }

    public DeviceLogAppendResult appendBatch(
            String tenantId,
            String deviceId,
            String serialNumber,
            List<PendingLogLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return DeviceLogAppendResult.empty();
        }

        String normalizedTenant = tenantId.trim();
        String normalizedDevice = deviceId.trim().toUpperCase();
        String normalizedSerial =
                serialNumber == null || serialNumber.isBlank()
                        ? normalizedDevice
                        : serialNumber.trim();

        synchronized (lockFor(normalizedTenant, normalizedDevice)) {
            Path logFile = logFilePath(normalizedTenant, normalizedDevice);
            Path metaFile = metaFilePath(normalizedTenant, normalizedDevice);
            boolean fileReset = resetIfOversized(logFile, metaFile, normalizedTenant, normalizedDevice);

            long lastSeq = readLastSeq(metaFile);
            List<DeviceLogEntry> entries = new ArrayList<>(lines.size());
            Instant receivedAt = Instant.now();

            try {
                Files.createDirectories(logFile.getParent());
                for (PendingLogLine line : lines) {
                    lastSeq++;
                    DeviceLogEntry entry =
                            new DeviceLogEntry(
                                    lastSeq,
                                    normalizedTenant,
                                    normalizedDevice,
                                    normalizedSerial,
                                    line.ts(),
                                    line.message(),
                                    receivedAt);
                    entries.add(entry);
                    String json = objectMapper.writeValueAsString(toJsonNode(entry)) + "\n";
                    Files.writeString(
                            logFile,
                            json,
                            StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                }
                writeLastSeq(metaFile, lastSeq);
            } catch (IOException e) {
                log.warn(
                        "Failed to append device logs for {}/{}",
                        normalizedTenant,
                        normalizedDevice,
                        e);
                return DeviceLogAppendResult.empty();
            }

            return new DeviceLogAppendResult(fileReset, entries);
        }
    }

    public List<DeviceLogEntry> readAfterSeq(String tenantId, String deviceId, long afterSeq) {
        return readAfterSeq(tenantId, deviceId, afterSeq, DEFAULT_CATCH_UP_LIMIT);
    }

    public List<DeviceLogEntry> readAfterSeq(
            String tenantId, String deviceId, long afterSeq, int limit) {
        String normalizedTenant = tenantId.trim();
        String normalizedDevice = deviceId.trim().toUpperCase();

        synchronized (lockFor(normalizedTenant, normalizedDevice)) {
            Path logFile = logFilePath(normalizedTenant, normalizedDevice);
            if (!Files.isRegularFile(logFile)) {
                return List.of();
            }

            List<DeviceLogEntry> result = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && result.size() < limit) {
                    if (line.isBlank()) {
                        continue;
                    }
                    DeviceLogEntry entry = parseLine(line);
                    if (entry != null && entry.seq() > afterSeq) {
                        result.add(entry);
                    }
                }
            } catch (IOException e) {
                log.warn(
                        "Failed to read device logs for {}/{} after seq {}",
                        normalizedTenant,
                        normalizedDevice,
                        afterSeq,
                        e);
            }
            return result;
        }
    }

    public Optional<Path> resolveLogFile(String tenantId, String deviceId) {
        Path path = logFilePath(tenantId.trim(), deviceId.trim().toUpperCase());
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public InputStream openDownloadStream(String tenantId, String deviceId) throws IOException {
        Path path =
                resolveLogFile(tenantId, deviceId)
                        .orElseThrow(
                                () ->
                                        new java.io.FileNotFoundException(
                                                "Log file not found for device " + deviceId));
        return Files.newInputStream(path);
    }

    private boolean resetIfOversized(
            Path logFile, Path metaFile, String tenantId, String deviceId) {
        try {
            if (!Files.isRegularFile(logFile)) {
                return false;
            }
            long size = Files.size(logFile);
            if (size < maxFileBytes) {
                return false;
            }
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(metaFile);
            log.info(
                    "Deleted device log file for {}/{} (size {} bytes >= limit {})",
                    tenantId,
                    deviceId,
                    size,
                    maxFileBytes);
            return true;
        } catch (IOException e) {
            log.warn("Failed to reset oversized log file for {}/{}", tenantId, deviceId, e);
            return false;
        }
    }

    private long readLastSeq(Path metaFile) {
        if (!Files.isRegularFile(metaFile)) {
            return 0L;
        }
        try {
            JsonNode node = objectMapper.readTree(metaFile.toFile());
            JsonNode lastSeqNode = node.get("lastSeq");
            return lastSeqNode != null && lastSeqNode.isNumber() ? lastSeqNode.asLong() : 0L;
        } catch (IOException e) {
            log.warn("Failed to read log meta file {}", metaFile, e);
            return 0L;
        }
    }

    private void writeLastSeq(Path metaFile, long lastSeq) throws IOException {
        Files.createDirectories(metaFile.getParent());
        objectMapper
                .writeValue(
                        metaFile.toFile(),
                        objectMapper.createObjectNode().put("lastSeq", lastSeq));
    }

    private DeviceLogEntry parseLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            Instant receivedAt = Instant.parse(node.get("receivedAt").asText());
            return new DeviceLogEntry(
                    node.get("seq").asLong(),
                    node.get("tenantId").asText(),
                    node.get("deviceId").asText(),
                    node.hasNonNull("serialNumber") ? node.get("serialNumber").asText() : "",
                    node.get("ts").asText(),
                    node.get("message").asText(),
                    receivedAt);
        } catch (Exception e) {
            log.debug("Skipping malformed log line: {}", line, e);
            return null;
        }
    }

    private static java.util.Map<String, Object> toJsonNode(DeviceLogEntry entry) {
        return java.util.Map.of(
                "seq", entry.seq(),
                "tenantId", entry.tenantId(),
                "deviceId", entry.deviceId(),
                "serialNumber", entry.serialNumber(),
                "ts", entry.ts(),
                "message", entry.message(),
                "receivedAt", entry.receivedAt().toString());
    }

    private Path logFilePath(String tenantId, String deviceId) {
        return basePath.resolve("tenants").resolve(tenantId).resolve("devices").resolve(deviceId + ".ndjson");
    }

    private Path metaFilePath(String tenantId, String deviceId) {
        return logFilePath(tenantId, deviceId).resolveSibling(deviceId + ".ndjson.meta");
    }

    private Object lockFor(String tenantId, String deviceId) {
        return locks.computeIfAbsent(tenantId + ":" + deviceId, ignored -> new Object());
    }

    public record PendingLogLine(String ts, String message) {}
}
