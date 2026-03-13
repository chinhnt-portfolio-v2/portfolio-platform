package dev.chinh.portfolio.platform.websocket;

import dev.chinh.portfolio.platform.metrics.ProjectHealthDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WebSocket connected: {}", session.getId());
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

    public void broadcast(ProjectHealthDto dto) {
        if (sessions.isEmpty()) {
            log.debug("No WebSocket clients connected, skipping broadcast");
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(dto);
        } catch (IOException e) {
            log.error("Failed to serialize ProjectHealthDto for broadcast", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("Failed to send WebSocket message to {}: {}", session.getId(), e.getMessage());
            }
        }
        log.debug("Broadcast metrics to {} clients: {}", sessions.size(), dto.projectSlug());
    }
}
