package dev.chinh.portfolio.apps.quiz.controller;

import dev.chinh.portfolio.apps.quiz.dto.*;
import dev.chinh.portfolio.apps.quiz.service.QuestionBankService;
import dev.chinh.portfolio.apps.quiz.service.QuizService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import dev.chinh.portfolio.shared.error.ErrorDetail;
import dev.chinh.portfolio.shared.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quiz")
public class QuizController {

    private final QuizService quizService;
    private final QuestionBankService questionBankService;

    public QuizController(QuizService quizService, QuestionBankService questionBankService) {
        this.quizService = quizService;
        this.questionBankService = questionBankService;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<TopicStatsResponse>> getTopics(@CurrentUser UUID userId) {
        return ResponseEntity.ok(quizService.getTopicsWithStats(userId));
    }

    @GetMapping("/questions/next")
    public ResponseEntity<QuizQuestionResponse> getNextQuestion(
            @CurrentUser UUID userId,
            @RequestParam List<String> topics,
            @RequestParam(defaultValue = "1") int limit,
            @RequestParam(required = false) List<Long> exclude,
            @RequestParam(required = false) String level,
            HttpServletRequest request) {
        return ResponseEntity.ok(quizService.getNextQuestion(userId, topics, limit, exclude, request));
    }

    @PostMapping("/attempts")
    public ResponseEntity<AttemptAnswerResponse> submitAnswer(
            @CurrentUser UUID userId,
            @Valid @RequestBody AttemptAnswerRequest req) {
        return ResponseEntity.ok(quizService.submitAnswer(userId, req));
    }

    @GetMapping("/attempts/missed")
    public ResponseEntity<List<MissedQuestionResponse>> getMissedQuestions(@CurrentUser UUID userId) {
        return ResponseEntity.ok(quizService.getMissedQuestions(userId));
    }

    @GetMapping("/progress/{topicSlug}")
    public ResponseEntity<QuizProgressResponse> getProgress(
            @CurrentUser UUID userId,
            @PathVariable String topicSlug) {
        return ResponseEntity.ok(quizService.getProgressByTopic(userId, topicSlug));
    }

    @GetMapping("/attempts")
    public ResponseEntity<Page<AttemptHistoryResponse>> getAttemptHistory(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) Boolean isCorrect,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(quizService.getAttemptHistory(userId, topic, isCorrect, from, to, pageable));
    }

    @DeleteMapping("/progress/{topicSlug}")
    public ResponseEntity<Void> resetProgress(@CurrentUser UUID userId, @PathVariable String topicSlug) {
        quizService.resetProgress(userId, topicSlug);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seed/status")
    public ResponseEntity<QuestionBankService.SeedStatus> getSeedStatus() {
        return ResponseEntity.ok(questionBankService.getSeedStatus());
    }

    /**
     * Manually trigger a re-seed of the question bank.
     * Resets all existing questions and re-loads from JSON seed files.
     */
    @PostMapping("/seed")
    public ResponseEntity<?> triggerSeed() {
        try {
            QuestionBankService.SeedStatus status = questionBankService.seedQuestionBank();
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse(new ErrorDetail("SEED_FAILED", e.getMessage())));
        }
    }
}
