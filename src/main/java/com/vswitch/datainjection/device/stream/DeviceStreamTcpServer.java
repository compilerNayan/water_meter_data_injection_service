package com.vswitch.datainjection.device.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.datainjection.device.stream.command.DeviceStreamConnectionRegistry;
import com.vswitch.datainjection.device.stream.command.DeviceStreamSession;
import com.vswitch.datainjection.device.stream.handler.DeviceStreamLineRouter;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelope;
import com.vswitch.datainjection.device.stream.protocol.DeviceStreamEnvelopeParser;

import jakarta.annotation.PreDestroy;

/**
 * Accepts newline-delimited JSON from IoT devices over plain TCP (port 9100).
 *
 * <p>Supports legacy flat pulse lines and v1 category envelopes (enrollment_request, device_message,
 * water_pulse, etc.).
 */
@Component
@ConditionalOnProperty(name = "device.stream.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceStreamTcpServer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamTcpServer.class);

    private final DeviceStreamIngestionService ingestionService;
    private final DeviceStreamConnectionRegistry connectionRegistry;
    private final DeviceStreamLineRouter lineRouter;
    private final ObjectMapper objectMapper;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService clientExecutor;

    DeviceStreamTcpServer(
            DeviceStreamIngestionService ingestionService,
            DeviceStreamConnectionRegistry connectionRegistry,
            DeviceStreamLineRouter lineRouter,
            ObjectMapper objectMapper,
            @Value("${device.stream.port:9100}") int port) {
        this.ingestionService = ingestionService;
        this.connectionRegistry = connectionRegistry;
        this.lineRouter = lineRouter;
        this.objectMapper = objectMapper;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        acceptExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "device-stream-accept"));
        clientExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "device-stream-client"));
        acceptExecutor.execute(this::acceptLoop);
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(port));
            running.set(true);
            log.info("Device stream TCP server listening on port {}", port);

            while (running.get()) {
                Socket client = serverSocket.accept();
                clientExecutor.execute(() -> handleClient(client));
            }
        } catch (IOException e) {
            if (running.get()) {
                log.error("Device stream TCP server failed on port {}", port, e);
            }
        }
    }

    private void handleClient(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        log.info("Device stream connected from {}", remote);

        DeviceStreamPulseParser pulseParser = new DeviceStreamPulseParser(objectMapper);
        DeviceStreamEnvelopeParser envelopeParser = new DeviceStreamEnvelopeParser(objectMapper);

        try (socket;
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                Writer writer =
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {

            DeviceStreamSession session = new DeviceStreamSession(writer);
            connectionRegistry.registerSession(session);

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        if (envelopeParser.looksLikeEnvelope(line)) {
                            handleEnvelopeLine(line, envelopeParser, session);
                        } else {
                            handleLegacyPulseLine(line, pulseParser, session);
                        }
                        writer.write("{\"ok\":true}\n");
                        writer.flush();
                    } catch (IllegalArgumentException e) {
                        log.warn("Rejected device stream line from {}: {}", remote, e.getMessage());
                        writer.write(
                                "{\"ok\":false,\"error\":\""
                                        + escapeJson(e.getMessage())
                                        + "\"}\n");
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                log.debug("Device stream disconnected from {} ({})", remote, e.getMessage());
            } finally {
                connectionRegistry.unregisterSession(session);
                log.info("Device stream closed from {}", remote);
            }
        } catch (IOException e) {
            log.debug("Device stream I/O error from {} ({})", remote, e.getMessage());
        }
    }

    private void handleEnvelopeLine(
            String line, DeviceStreamEnvelopeParser envelopeParser, DeviceStreamSession session) {
        DeviceStreamEnvelope envelope = envelopeParser.parseEnvelope(line);

        if ("water_pulse".equals(envelope.category())) {
            bindSerial(envelope, session);
            handleWaterPulseEnvelope(envelope);
            return;
        }

        lineRouter.routeEnvelope(envelope, session);
    }

    private void bindSerial(DeviceStreamEnvelope envelope, DeviceStreamSession session) {
        if (envelope.serialNumber() == null || envelope.serialNumber().isBlank()) {
            return;
        }
        session.bindSerialNumber(envelope.serialNumber());
        connectionRegistry.bindSerial(session, envelope.serialNumber());
    }

    private void handleWaterPulseEnvelope(DeviceStreamEnvelope envelope) {
        if (envelope.data() == null || envelope.data().isNull()) {
            throw new IllegalArgumentException("water_pulse envelope missing data");
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode merged =
                    (com.fasterxml.jackson.databind.node.ObjectNode) envelope.data().deepCopy();
            if (!merged.hasNonNull("tenantId") && envelope.tenantId() != null) {
                merged.put("tenantId", envelope.tenantId());
            }
            if (!merged.hasNonNull("serialNumber") && envelope.serialNumber() != null) {
                merged.put("serialNumber", envelope.serialNumber());
            }
            DeviceStreamPulseParser pulseParser = new DeviceStreamPulseParser(objectMapper);
            DeviceStreamPulsePayload payload =
                    pulseParser.parseLine(objectMapper.writeValueAsString(merged));
            ingestionService.ingestPulse(payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid water_pulse envelope data", e);
        }
    }

    private void handleLegacyPulseLine(
            String line, DeviceStreamPulseParser pulseParser, DeviceStreamSession session) {
        DeviceStreamPulsePayload payload = pulseParser.parseLine(line);
        if (payload.serialNumber() != null && !payload.serialNumber().isBlank()) {
            session.bindSerialNumber(payload.serialNumber());
            connectionRegistry.bindSerial(session, payload.serialNumber());
        }
        ingestionService.ingestPulse(payload);
    }

    @PreDestroy
    void shutdown() {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }
        log.info("Device stream TCP server stopped");
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
