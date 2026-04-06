package dev.chinh.portfolio.apps.quiz.dto;

/**
 * Coverage breakdown for a single topic, returned by GET /api/v1/quiz/progress/:topicSlug
 */
public record QuizProgressResponse(
    String topicSlug,
    String userLevel,
    LevelBreakdown junior,
    LevelBreakdown middle,
    LevelBreakdown senior
) {
    public record LevelBreakdown(
        int mastered,    // questions with repetitions >= 2
        int total,
        double percent  // 0.0–1.0
    ) {}
}
