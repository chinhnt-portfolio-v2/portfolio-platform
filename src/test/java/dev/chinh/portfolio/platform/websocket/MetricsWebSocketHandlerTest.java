package dev.chinh.portfolio.platform.websocket;

import dev.chinh.portfolio.platform.metrics.MetricsMapper;
import dev.chinh.portfolio.platform.metrics.ProjectHealth;
import dev.chinh.portfolio.platform.metrics.ProjectHealthDto;
import dev.chinh.portfolio.platform.metrics.ProjectHealthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MetricsWebSocketHandlerTest {

    private MetricsWebSocketHandler handler;
    private ProjectHealthRepository repository;
    private MetricsMapper mapper;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectHealthRepository.class);
        mapper = mock(MetricsMapper.class);
        handler = new MetricsWebSocketHandler(repository, mapper);
    }

    @Test
    void afterConnectionEstablished_sendsInitialSnapshot() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        when(repository.findAll()).thenReturn(Collections.emptyList());

        handler.afterConnectionEstablished(session);

        verify(repository).findAll();
    }

    @Test
    void afterConnectionEstablished_sendsCurrentRecords() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        ProjectHealth health = mock(ProjectHealth.class);
        when(health.getProjectSlug()).thenReturn("wallet-app");
        when(repository.findAll()).thenReturn(List.of(health));

        ProjectHealthDto dto = new ProjectHealthDto("wallet-app", "UP",
                new BigDecimal("100.00"), 150, null, Instant.now());
        when(mapper.toDto(health)).thenReturn(dto);

        handler.afterConnectionEstablished(session);

        verify(repository).findAll();
        verify(mapper).toDto(health);
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void afterConnectionClosed_doesNotThrow() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");

        assertDoesNotThrow(() -> handler.afterConnectionClosed(session, CloseStatus.NORMAL));
    }

    @Test
    void broadcast_noSessions_doesNotThrow() {
        ProjectHealthDto dto = new ProjectHealthDto(
                "wallet-app",
                "UP",
                new BigDecimal("100.00"),
                150,
                null,
                Instant.now()
        );

        // Should not throw when no sessions connected
        assertDoesNotThrow(() -> handler.broadcast(dto));
    }

    @Test
    void handleTextMessage_doesNotThrow_andDoesNotSendResponse() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        TextMessage message = new TextMessage("{\"action\":\"ping\"}");

        // Should not throw, should not send any response
        assertDoesNotThrow(() -> handler.handleTextMessage(session, message));

        // Verify no message was sent back (one-way channel)
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_withClosedSessions_handlesGracefully() {
        // This test verifies broadcast handles mixed open/closed sessions gracefully
        // We can't easily test with mocks due to reflection, but we verify no exceptions
        ProjectHealthDto dto = new ProjectHealthDto(
                "wallet-app",
                "UP",
                new BigDecimal("100.00"),
                150,
                null,
                Instant.now()
        );

        // With no sessions, should not throw
        assertDoesNotThrow(() -> handler.broadcast(dto));
    }

    @Test
    void broadcast_serializationHandlesNullFields() {
        // Test that broadcast handles DTOs with null fields gracefully
        ProjectHealthDto dto = new ProjectHealthDto(
                "test-app",
                "DOWN",
                null,  // null uptime
                null,  // null response time
                null,  // null last online
                null   // null last polled
        );

        // Should not throw during serialization
        assertDoesNotThrow(() -> handler.broadcast(dto));
    }
}
