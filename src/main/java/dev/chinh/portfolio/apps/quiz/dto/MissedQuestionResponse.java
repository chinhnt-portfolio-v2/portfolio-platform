package dev.chinh.portfolio.apps.quiz.dto;

import java.time.Instant;

/**
 * One question the user has answered incorrectly.
 * Returned by GET /api/v1/quiz/attempts/missed
 */
public record MissedQuestionResponse(
    Long questionId,
    String topicSlug,
    String levelTag,
    String questionText,
    String userAnswer,
    String correctKey,
    String explanation,
    Instant attemptedAt
) {}
