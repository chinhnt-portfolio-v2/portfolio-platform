package dev.chinh.portfolio.platform.websocket;

import dev.chinh.portfolio.platform.metrics.MetricsMapper;
import dev.chinh.portfolio.platform.metrics.ProjectHealthDto;
import dev.chinh.portfolio.platform.metrics.ProjectHealthRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ProjectHealthRepository projectHealthRepository;
    private final MetricsMapper metricsMapper;

    public MetricsWebSocketHandler(ProjectHealthRepository projectHealthRepository,
                                  MetricsMapper metricsMapper) {
        this.projectHealthRepository = projectHealthRepository;
        this.metricsMapper = metricsMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WebSocket connected: {}", session.getId());

        // Send initial snapshot: all current ProjectHealth records from DB.
        // This ensures a newly connected client immediately receives data without
        // waiting for the next scheduler poll cycle (60s).
        sendInitialSnapshot(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // One-way: BE → FE only. Incoming messages are ignored.
        log.debug("Received message from {} (ignored - one-way channel): {}", session.getId(), message.getPayload());
    }

    /**
     * Send the current snapshot of all ProjectHealth records to a specific session.
     * Called once when a new WebSocket client connects.
     */
    private void sendInitialSnapshot(WebSocketSession session) {
        List<ProjectHealthDto> snapshots = projectHealthRepository.findAll().stream()
                .map(metricsMapper::toDto)
                .toList();

        if (snapshots.isEmpty()) {
            log.debug("No ProjectHealth records yet; initial snapshot skipped");
            return;
        }

        log.debug("Sending initial snapshot ({} records) to new client: {}", snapshots.size(), session.getId());
        for (ProjectHealthDto dto : snapshots) {
            send(session, dto);
        }
    }

    /**
     * Broadcast a single ProjectHealth update to all connected clients.
     */
    public void broadcast(ProjectHealthDto dto) {
        if (sessions.isEmpty()) {
            log.debug("No WebSocket clients connected, skipping broadcast");
            return;
        }

        for (WebSocketSession session : sessions) {
            send(session, dto);
        }
        log.debug("Broadcast metrics to {} clients: {}", sessions.size(), dto.projectSlug());
    }

    private void send(WebSocketSession session, ProjectHealthDto dto) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(dto);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message to {}: {}", session.getId(), e.getMessage());
        }
    }
}

