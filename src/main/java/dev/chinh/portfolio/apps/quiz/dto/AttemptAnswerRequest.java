package dev.chinh.portfolio.apps.quiz.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/quiz/attempts
 */
public record AttemptAnswerRequest(
    @NotNull Long questionId,
    @NotNull String givenKey,   // "a" or "a,b" for multiple-answer
    Integer responseMs
) {}
