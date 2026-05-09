package dev.chinh.portfolio.apps.quiz.dto;

import java.time.Instant;

public record AttemptHistoryResponse(
    Long questionId,
    String topicSlug,
    String levelTag,
    String questionText,
    String givenKey,
    String correctKey,
    boolean isCorrect,
    Integer responseMs,
    Instant attemptedAt
) {}
