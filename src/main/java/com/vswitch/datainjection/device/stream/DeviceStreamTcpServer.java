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

import jakarta.annotation.PreDestroy;

/**
 * Accepts newline-delimited JSON pulses from IoT devices over plain TCP.
 *
 * <p>Example line:
 * {"tenantId":"63tk0y1","serialNumber":"QJPDXN094","ml":45,"cumulativeLiters":123.456,"ts":"2026-06-13T10:00:05Z"}
 */
@Component
@ConditionalOnProperty(name = "device.stream.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceStreamTcpServer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceStreamTcpServer.class);

    private final DeviceStreamIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService clientExecutor;

    DeviceStreamTcpServer(
            DeviceStreamIngestionService ingestionService,
            ObjectMapper objectMapper,
            @Value("${device.stream.port:9100}") int port) {
        this.ingestionService = ingestionService;
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
        DeviceStreamPulseParser parser = new DeviceStreamPulseParser(objectMapper);

        try (socket;
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                Writer writer =
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    DeviceStreamPulsePayload payload = parser.parseLine(line);
                    ingestionService.ingestPulse(payload);
                    writer.write("{\"ok\":true}\n");
                    writer.flush();
                } catch (IllegalArgumentException e) {
                    log.warn("Rejected device stream line from {}: {}", remote, e.getMessage());
                    writer.write("{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}\n");
                    writer.flush();
                }
            }
        } catch (IOException e) {
            log.debug("Device stream disconnected from {} ({})", remote, e.getMessage());
        } finally {
            log.info("Device stream closed from {}", remote);
        }
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
