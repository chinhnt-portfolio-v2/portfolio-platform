-- V3__create_analytics_events_table.sql
-- Portfolio v2 — Story 6-1: Real-time Analytics Dashboard
-- Stores anonymised page-view and traffic-source events. No PII is stored.

-- ─────────────────────────────────────────────────────────
-- analytics_events
-- ─────────────────────────────────────────────────────────
CREATE TABLE analytics_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(50)  NOT NULL,                  -- 'page_view' | 'traffic_source'
    route           VARCHAR(255) NOT NULL,                  -- e.g. '/', '/projects/wallet-app'
    visitor_id      VARCHAR(64),                             -- SHA-256 hash of browser fingerprint (no PII)
    traffic_source  VARCHAR(30),                             -- 'direct' | 'referral' | 'organic'
    referrer_domain VARCHAR(255),                            -- e.g. 'github.com' (no full URL stored)
    country_code    VARCHAR(10),                             -- derived from IP — stored as ISO 3166-1 alpha-2
    device_type     VARCHAR(20),                             -- 'desktop' | 'mobile' | 'tablet'
    session_id      VARCHAR(64),                             -- opaque session token from sessionStorage
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns used by AnalyticsService
CREATE INDEX idx_analytics_occurred_at         ON analytics_events(occurred_at);
CREATE INDEX idx_analytics_route_occurred_at   ON analytics_events(route, occurred_at);
CREATE INDEX idx_analytics_event_type_occurred ON analytics_events(event_type, occurred_at);
