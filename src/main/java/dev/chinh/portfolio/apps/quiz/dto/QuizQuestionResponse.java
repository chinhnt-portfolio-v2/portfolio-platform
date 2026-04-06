package dev.chinh.portfolio.apps.quiz.dto;

import java.util.List;

/**
 * Single question returned to FE.
 * correctKey and explanation are intentionally omitted —
 * they are only returned after the user submits an answer.
 */
public record QuizQuestionResponse(
    Long id,
    String topicSlug,
    String levelTag,
    String questionText,
    String questionType,
    List<QuizOption> options
) {
    public record QuizOption(String id, String text) {}
}
