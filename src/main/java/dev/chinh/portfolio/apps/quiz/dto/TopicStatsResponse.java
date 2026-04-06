package dev.chinh.portfolio.apps.quiz.dto;

/**
 * Per-topic stats shown on topic selection screen.
 */
public record TopicStatsResponse(
    String topicSlug,
    String topicLabel,
    long questionCount,
    Coverage coverage,
    String userLevel,
    int currentStreak
) {
    /**
     * Coverage percentage per level.
     * Values are 0.0–1.0 (e.g. 0.65 = 65%).
     */
    public record Coverage(
        double junior,
        double middle,
        double senior
    ) {}
}
