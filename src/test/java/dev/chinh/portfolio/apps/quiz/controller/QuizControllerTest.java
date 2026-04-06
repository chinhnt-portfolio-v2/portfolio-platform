package dev.chinh.portfolio.apps.quiz.controller;

import dev.chinh.portfolio.apps.quiz.dto.*;
import dev.chinh.portfolio.apps.quiz.service.QuizService;
import dev.chinh.portfolio.apps.quiz.service.QuestionBankService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuizController.
 * Uses the same plain-unit-test pattern as WalletDashboardMonthlyTest:
 * controller instantiated directly with mocked services, no Spring context.
 */
class QuizControllerTest {

    private final QuizService quizServiceMock   = mock(QuizService.class);
    private final QuestionBankService bankMock  = mock(QuestionBankService.class);
    private final QuizController controller      = new QuizController(quizServiceMock, bankMock);

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/quiz/topics
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/quiz/topics")
    class GetTopicsTests {

        @Test
        @DisplayName("returns 200 with topic list")
        void getTopics_returnsTopicList() {
            List<TopicStatsResponse> mockTopics = List.of(
                    new TopicStatsResponse(
                            "java-core", "Java Core", 10,
                            new TopicStatsResponse.Coverage(0.4, 0.25, 0.0),
                            "MIDDLE", 3)
            );
            when(quizServiceMock.getTopicsWithStats(USER_ID)).thenReturn(mockTopics);

            ResponseEntity<List<TopicStatsResponse>> response = controller.getTopics(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).topicSlug()).isEqualTo("java-core");
            assertThat(response.getBody().get(0).userLevel()).isEqualTo("MIDDLE");
            verify(quizServiceMock).getTopicsWithStats(USER_ID);
        }

        @Test
        @DisplayName("returns empty list when no topics")
        void getTopics_emptyWhenNoTopics() {
            when(quizServiceMock.getTopicsWithStats(USER_ID)).thenReturn(List.of());

            ResponseEntity<List<TopicStatsResponse>> response = controller.getTopics(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/quiz/attempts
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/quiz/attempts")
    class SubmitAttemptTests {

        @Test
        @DisplayName("returns 200 with attempt response on correct answer")
        void submitAnswer_correct_returnsAttemptResponse() {
            SRSessionState srState = new SRSessionState(
                    new BigDecimal("2.60"), 1, 1, 1, 0, false, Instant.now().plusSeconds(86400));
            AttemptAnswerResponse mockResp = new AttemptAnswerResponse(
                    true, "a", "Explanation here", srState, 1);
            when(quizServiceMock.submitAnswer(eq(USER_ID), any(AttemptAnswerRequest.class)))
                    .thenReturn(mockResp);

            AttemptAnswerRequest req = new AttemptAnswerRequest(1L, "a", 1500);
            ResponseEntity<AttemptAnswerResponse> response = controller.submitAnswer(USER_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isCorrect()).isTrue();
            assertThat(response.getBody().correctKey()).isEqualTo("a");
            assertThat(response.getBody().explanation()).isEqualTo("Explanation here");
            assertThat(response.getBody().srState().repetitions()).isEqualTo(1);
            assertThat(response.getBody().srState().easeFactor())
                    .isEqualByComparingTo(new BigDecimal("2.60"));
        }

        @Test
        @DisplayName("returns 200 with attempt response on wrong answer")
        void submitAnswer_wrong_returnsAttemptResponse() {
            SRSessionState srState = new SRSessionState(
                    new BigDecimal("2.30"), 1, 1, 0, 1, true, Instant.now().plusSeconds(86400));
            AttemptAnswerResponse mockResp = new AttemptAnswerResponse(
                    false, "b", "Wrong – here is why", srState, 0);
            when(quizServiceMock.submitAnswer(eq(USER_ID), any(AttemptAnswerRequest.class)))
                    .thenReturn(mockResp);

            AttemptAnswerRequest req = new AttemptAnswerRequest(2L, "wrong", 3000);
            ResponseEntity<AttemptAnswerResponse> response = controller.submitAnswer(USER_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isCorrect()).isFalse();
            assertThat(response.getBody().srState().isRelearning()).isTrue();
            assertThat(response.getBody().srState().consecutiveWrong()).isEqualTo(1);
        }

        @Test
        @DisplayName("passes the correct userId and request to service")
        void submitAnswer_passesCorrectParams() {
            AttemptAnswerResponse mockResp = new AttemptAnswerResponse(
                    true, "c", "OK", null, 1);
            when(quizServiceMock.submitAnswer(eq(USER_ID), any(AttemptAnswerRequest.class)))
                    .thenReturn(mockResp);

            AttemptAnswerRequest req = new AttemptAnswerRequest(5L, "c", 500);
            controller.submitAnswer(USER_ID, req);

            verify(quizServiceMock).submitAnswer(eq(USER_ID), eq(req));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/quiz/attempts/missed
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/quiz/attempts/missed")
    class GetMissedQuestionsTests {

        @Test
        @DisplayName("returns 200 with missed question list")
        void getMissedQuestions_returnsMissedList() {
            Instant now = Instant.now();
            List<MissedQuestionResponse> mockMissed = List.of(
                    new MissedQuestionResponse(
                            5L, "java-core", "JUNIOR",
                            "What is a marker interface?",
                            "b", "a",
                            "Marker interfaces like Serializable have no methods.",
                            now)
            );
            when(quizServiceMock.getMissedQuestions(USER_ID)).thenReturn(mockMissed);

            ResponseEntity<List<MissedQuestionResponse>> response = controller.getMissedQuestions(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).questionId()).isEqualTo(5L);
            assertThat(response.getBody().get(0).userAnswer()).isEqualTo("b");
            assertThat(response.getBody().get(0).correctKey()).isEqualTo("a");
            assertThat(response.getBody().get(0).topicSlug()).isEqualTo("java-core");
        }

        @Test
        @DisplayName("returns empty list when no missed questions")
        void getMissedQuestions_emptyWhenNone() {
            when(quizServiceMock.getMissedQuestions(USER_ID)).thenReturn(List.of());

            ResponseEntity<List<MissedQuestionResponse>> response = controller.getMissedQuestions(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }
}
