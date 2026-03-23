package dev.chinh.portfolio.platform.metrics;

import org.springframework.context.ApplicationEvent;

/**
 * Domain event published whenever ProjectHealth is updated.
 * MetricsWebSocketHandler listens to this to broadcast to clients.
 * This decouples the polling service from the WebSocket handler,
 * breaking the circular dependency.
 */
public class ProjectHealthUpdatedEvent extends ApplicationEvent {

    private final ProjectHealthDto dto;

    public ProjectHealthUpdatedEvent(Object source, ProjectHealthDto dto) {
        super(source);
        this.dto = dto;
    }

    public ProjectHealthDto getDto() {
        return dto;
    }
}
