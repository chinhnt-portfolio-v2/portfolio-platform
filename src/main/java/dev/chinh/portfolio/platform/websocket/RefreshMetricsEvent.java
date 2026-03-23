package dev.chinh.portfolio.platform.websocket;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when a new WebSocket client connects and needs fresh data.
 * MetricsAggregationService listens to this to trigger an immediate poll.
 * This decouples the two classes, breaking the circular dependency.
 */
public class RefreshMetricsEvent extends ApplicationEvent {

    public RefreshMetricsEvent(Object source) {
        super(source);
    }
}
