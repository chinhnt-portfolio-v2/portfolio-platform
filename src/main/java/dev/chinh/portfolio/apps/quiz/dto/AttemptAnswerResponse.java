package dev.chinh.portfolio.apps.quiz.dto;

/**
 * Response after submitting an answer.
 */
public record AttemptAnswerResponse(
    boolean isCorrect,
    String correctKey,
    String explanation,
    SRSessionState srState,
    int streakDays
) {}
