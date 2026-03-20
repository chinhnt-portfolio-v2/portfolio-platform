package dev.chinh.portfolio.platform.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Lightweight request payload sent by the frontend analytics service.
 * Only identifiers are sent — no PII is stored.
 *
 * @param eventType     "page_view" or "traffic_source"
 * @param route         Current page path (e.g. "/" or "/projects/wallet-app")
 * @param visitorId     SHA-256 hash of the browser fingerprint (not raw PII)
 * @param sessionId     Opaque session token stored in sessionStorage
 * @param trafficSource "direct" | "referral" | "organic" (only for traffic_source events)
 */
public record TrackEventRequest(
        @NotBlank @Pattern(regexp = "^(page_view|traffic_source)$") String eventType,
        @NotBlank String route,
        @JsonProperty("visitorId") String visitorId,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("trafficSource") String trafficSource
) {}
