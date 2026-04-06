package dev.chinh.portfolio.apps.quiz.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SM-2 spaced repetition state, returned after each attempt.
 */
public record SRSessionState(
    BigDecimal easeFactor,
    int intervalDays,
    int repetitions,
    int consecutiveCorrect,
    int consecutiveWrong,
    boolean isRelearning,
    Instant nextReviewAt
) {}
