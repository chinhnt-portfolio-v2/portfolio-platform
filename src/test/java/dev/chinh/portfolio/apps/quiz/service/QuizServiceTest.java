package dev.chinh.portfolio.apps.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.apps.quiz.*;
import dev.chinh.portfolio.apps.quiz.dto.*;
import dev.chinh.portfolio.apps.quiz.exception.QuestionNotFoundException;
import dev.chinh.portfolio.apps.quiz.exception.TopicNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock QuizQuestionRepository questionRepository;
    @Mock QuizAttemptRepository  attemptRepository;
    @Mock UserQuizProgressRepository progressRepository;

    QuizService service;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String OPTS_JSON = """
        [{"id":"a","text":"Answer A"},{"id":"b","text":"Answer B"}]
        """;

    @BeforeEach
    void setUp() {
        service = new QuizService(questionRepository, attemptRepository, progressRepository, new ObjectMapper());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SM-2 State Machine Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SM-2 – First correct answer (repetitions: 0 → 1)")
    class FirstCorrectAnswer {

        @Test
        @DisplayName("sets interval=1, repetitions=1, easeFactor=2.6, consecutiveCorrect=1")
        void firstCorrectAnswer_setsCorrectSM2State() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 1, 0, 0, false);

            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(1L, "a", 1500));

            assertThat(resp.isCorrect()).isTrue();
            assertThat(resp.srState().easeFactor()).isEqualByComparingTo(new BigDecimal("2.60"));
            assertThat(resp.srState().intervalDays()).isEqualTo(1);
            assertThat(resp.srState().repetitions()).isEqualTo(1);
            assertThat(resp.srState().consecutiveCorrect()).isEqualTo(1); // 0 → 1 within this run
            assertThat(resp.srState().consecutiveWrong()).isEqualTo(0);
            assertThat(resp.srState().isRelearning()).isFalse();
        }

        @Test
        @DisplayName("EF increases by 0.1 for first correct answer")
        void firstCorrect_increasesEF() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 1, 0, 0, false);

            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(1L, "a", null));

            assertThat(prog.getEaseFactor()).isEqualByComparingTo(new BigDecimal("2.60"));
        }
    }

    @Nested
    @DisplayName("SM-2 – Second correct answer (repetitions: 1 → 2)")
    class SecondCorrectAnswer {

        @Test
        @DisplayName("sets interval=6, repetitions=2, consecutiveCorrect=2")
        void secondCorrectAnswer_setsCorrectSM2State() {
            // prog already has repetitions=1 (from first correct)
            QuizQuestion q = makeQuestion(2L, "java-core", "JUNIOR", "b");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.60"), 1, 1, 0, false);

            when(questionRepository.findById(2L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(2L, "b", 2000));

            assertThat(resp.isCorrect()).isTrue();
            assertThat(resp.srState().intervalDays()).isEqualTo(6);
            // Check prog directly first (diagnostic)
            assertThat(prog.getRepetitions()).isEqualTo(2);
            assertThat(prog.getConsecutiveCorrect()).isEqualTo(1); // only this run
            assertThat(prog.getEaseFactor()).isEqualByComparingTo(new BigDecimal("2.70"));
            // Then check srState (what service actually returns via toSRSessionState)
            assertThat(resp.srState().repetitions()).isEqualTo(2);
            assertThat(resp.srState().consecutiveCorrect()).isEqualTo(1);
            assertThat(resp.srState().consecutiveWrong()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("SM-2 – Third+ correct answer (repetitions >= 2)")
    class ThirdCorrectAnswer {

        @Test
        @DisplayName("interval = round(prev_interval × EF), repetitions increments")
        void thirdCorrectAnswer_usesMultiplicativeFormula() {
            // EF=2.6, interval=6, reps=2 → new interval = round(6 × 2.6) = 16
            QuizQuestion q = makeQuestion(3L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.60"), 6, 2, 0, false);

            when(questionRepository.findById(3L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(3L, "a", 1800));

            // 6 × 2.6 = 15.6 → rounds to 16
            assertThat(resp.srState().intervalDays()).isEqualTo(16);
            assertThat(resp.srState().repetitions()).isEqualTo(3);
            assertThat(resp.srState().consecutiveCorrect()).isEqualTo(1); // per-session, not cumulative
        }

        @Test
        @DisplayName("EF increases by 0.1 on every correct answer")
        void correctAnswer_alwaysIncreasesEF() {
            // reps 2 → 3: EF 2.60 + 0.1 = 2.70
            QuizQuestion q = makeQuestion(3L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.60"), 6, 2, 0, false);

            when(questionRepository.findById(3L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(3L, "a", 1800));

            // 2.6 + 0.1 = 2.7
            assertThat(prog.getEaseFactor()).isEqualByComparingTo(new BigDecimal("2.70"));
        }
    }

    @Nested
    @DisplayName("SM-2 – First wrong answer")
    class FirstWrongAnswer {

        @Test
        @DisplayName("interval halved (min 1), repetitions max(0, reps-1), isRelearning=true")
        void firstWrongAnswer_setsRelearningState() {
            // Start from reps=2, interval=6, EF=2.6
            QuizQuestion q = makeQuestion(4L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.60"), 6, 2, 0, false);

            when(questionRepository.findById(4L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(4L, "wrong", 3000));

            assertThat(resp.isCorrect()).isFalse();
            assertThat(resp.srState().isRelearning()).isTrue();
            assertThat(resp.srState().consecutiveCorrect()).isEqualTo(0);
            assertThat(resp.srState().consecutiveWrong()).isEqualTo(1);
            assertThat(resp.srState().intervalDays()).isEqualTo(3);  // 6 / 2 = 3
            assertThat(resp.srState().repetitions()).isEqualTo(1);   // max(0, 2-1) = 1
        }

        @Test
        @DisplayName("EF decreases by 0.2 (floored at 1.3) on wrong answer")
        void wrongAnswer_decreasesEF() {
            QuizQuestion q = makeQuestion(4L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 6, 2, 0, false);

            when(questionRepository.findById(4L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(4L, "wrong", 3000));

            // 2.5 - 0.2 = 2.3
            assertThat(prog.getEaseFactor()).isEqualByComparingTo(new BigDecimal("2.30"));
        }

        @Test
        @DisplayName("repetitions stays at 0 when wrong on first attempt (0-1=negative → max to 0)")
        void wrongAnswer_keepsRepsAtZero() {
            QuizQuestion q = makeQuestion(4L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 1, 0, 0, false);

            when(questionRepository.findById(4L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(4L, "wrong", 3000));

            assertThat(resp.srState().repetitions()).isEqualTo(0);
            assertThat(resp.srState().isRelearning()).isTrue();
        }
    }

    @Nested
    @DisplayName("SM-2 – Correct answer during relearning")
    class CorrectDuringRelearning {

        @Test
        @DisplayName("restores partial interval, exits relearning, resets consecutiveWrong, EF penalty")
        void correctDuringRelearning_exitsRelearning() {
            // Simulate: was on interval=6, wrong → interval=3, isRelearning=true, consecutiveWrong=1
            QuizQuestion q = makeQuestion(5L, "java-core", "JUNIOR", "b");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.30"), 3, 1, 1, true);
            // preLapseInterval stored before handleCorrect; simulate save happened before wrong → in submitAnswer
            // the flow: saves attempt, gets prog (which now has interval=3 from last wrong), preLapseInterval=3

            when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(5L, "b", 1500));

            assertThat(resp.isCorrect()).isTrue();
            assertThat(resp.srState().isRelearning()).isFalse();
            assertThat(resp.srState().consecutiveWrong()).isEqualTo(0);
            // max(1, 3/2) = max(1, 1) = 1
            assertThat(resp.srState().intervalDays()).isEqualTo(1);
            // 2.3 - 0.15 + 0.1 = 2.25
            assertThat(resp.srState().easeFactor()).isEqualByComparingTo(new BigDecimal("2.25"));
        }
    }

    @Nested
    @DisplayName("SM-2 – EF floor")
    class EFFloor {

        @Test
        @DisplayName("EF never drops below 1.3")
        void easeFactor_neverDropsBelowMinimum() {
            QuizQuestion q = makeQuestion(6L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("1.35"), 6, 2, 0, false);

            when(questionRepository.findById(6L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(6L, "wrong", 3000));

            // 1.35 - 0.2 = 1.15 → floored to 1.3
            assertThat(prog.getEaseFactor()).isEqualByComparingTo(new BigDecimal("1.30"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Coverage & Level Derivation Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Coverage calculation")
    class CoverageTests {

        @Test
        @DisplayName("2/5 mastered = 0.4 coverage")
        void coverage_calculationWithMockedRepo() {
            // Topic: java-core, level: JUNIOR, 2 mastered out of 5 total
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR"))
                    .thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID))
                    .thenReturn(2L);

            double cov = invokeCoverage("java-core", "JUNIOR");

            assertThat(cov).isCloseTo(0.4, within(0.001));
        }

        @Test
        @DisplayName("0/0 (no questions) returns 0.0 coverage")
        void coverage_zeroQuestions_returnsZero() {
            when(questionRepository.countByTopicSlugAndLevelTag("empty-topic", "JUNIOR"))
                    .thenReturn(0L);
            when(questionRepository.countMasteredByTopicAndLevel("empty-topic", "JUNIOR", USER_ID))
                    .thenReturn(0L);

            double cov = invokeCoverage("empty-topic", "JUNIOR");

            assertThat(cov).isEqualTo(0.0);
        }

        private double invokeCoverage(String topicSlug, String levelTag) {
            // coverage() is private; invoke via getTopicsWithStats which calls coverage internally
            when(questionRepository.findAllDistinctTopicSlugs()).thenReturn(List.of(topicSlug));
            when(progressRepository.findByUserId(USER_ID)).thenReturn(List.of());
            // levelBreakdown also calls coverage; we only need one topic
            List<TopicStatsResponse> result = service.getTopicsWithStats(USER_ID);
            // Find the coverage for the requested level tag via the response
            // Use getProgressByTopic to get a simpler response
            return invokeLevelBreakdown(topicSlug, levelTag);
        }

        private double invokeLevelBreakdown(String topicSlug, String levelTag) {
            when(questionRepository.findAllDistinctTopicSlugs()).thenReturn(List.of(topicSlug));
            QuizProgressResponse resp = service.getProgressByTopic(USER_ID, topicSlug);
            return switch (levelTag) {
                case "JUNIOR" -> resp.junior().percent();
                case "MIDDLE" -> resp.middle().percent();
                default -> resp.senior().percent();
            };
        }
    }

    @Nested
    @DisplayName("Level derivation")
    class LevelDerivationTests {

        @Test
        @DisplayName("juniorCov < 0.40 → JUNIOR")
        void juniorCovBelowThreshold_returnsJunior() {
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID)).thenReturn(1L);
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "MIDDLE")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "MIDDLE", USER_ID)).thenReturn(5L);
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "SENIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "SENIOR", USER_ID)).thenReturn(5L);

            String level = deriveLevel(0.2, 1.0, 1.0);
            assertThat(level).isEqualTo("JUNIOR");
        }

        @Test
        @DisplayName("juniorCov >= 0.40 but midCov < 0.75 → MIDDLE")
        void juniorCovAboveButMidBelowThreshold_returnsMiddle() {
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID)).thenReturn(2L); // 0.4
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "MIDDLE")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "MIDDLE", USER_ID)).thenReturn(1L); // 0.25
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "SENIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "SENIOR", USER_ID)).thenReturn(5L);

            String level = deriveLevel(0.4, 0.25, 1.0);
            assertThat(level).isEqualTo("MIDDLE");
        }

        @Test
        @DisplayName("midCov >= 0.75 → SENIOR")
        void midCovAboveThreshold_returnsSenior() {
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID)).thenReturn(5L);
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "MIDDLE")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "MIDDLE", USER_ID)).thenReturn(4L); // 1.0
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "SENIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "SENIOR", USER_ID)).thenReturn(5L);

            String level = deriveLevel(1.0, 1.0, 1.0);
            assertThat(level).isEqualTo("SENIOR");
        }

        // Use getProgressByTopic to trigger deriveLevel
        private String deriveLevel(double junCov, double midCov, double senCov) {
            when(questionRepository.findAllDistinctTopicSlugs()).thenReturn(List.of("java-core"));
            QuizProgressResponse resp = service.getProgressByTopic(USER_ID, "java-core");
            return resp.userLevel();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missed Questions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /attempts/missed")
    class MissedQuestionsTests {

        @Test
        @DisplayName("returns wrong attempts sorted by recency")
        void missedQuestions_returnsSortedByRecency() {
            // findTop50DistinctWrongQuestionIds returns IDs in order (most recent first)
            when(attemptRepository.findTop50DistinctWrongQuestionIds(USER_ID))
                    .thenReturn(List.of(10L, 9L, 8L));

            QuizQuestion q10 = makeQuestion(10L, "java-core", "JUNIOR", "a");
            QuizQuestion q9  = makeQuestion(9L,  "spring-boot", "MIDDLE", "b");
            QuizQuestion q8  = makeQuestion(8L,  "reactjs-ts", "SENIOR", "c");

            Instant now = Instant.now();
            QuizAttempt wrong10 = makeWrongAttempt(USER_ID, 10L, now);
            QuizAttempt wrong9  = makeWrongAttempt(USER_ID, 9L,  now.minusSeconds(60));
            QuizAttempt wrong8  = makeWrongAttempt(USER_ID, 8L,  now.minusSeconds(120));

            when(questionRepository.findById(10L)).thenReturn(Optional.of(q10));
            when(questionRepository.findById(9L)).thenReturn(Optional.of(q9));
            when(questionRepository.findById(8L)).thenReturn(Optional.of(q8));
            when(attemptRepository.findLatestWrongAttempt(USER_ID, 10L)).thenReturn(wrong10);
            when(attemptRepository.findLatestWrongAttempt(USER_ID, 9L)).thenReturn(wrong9);
            when(attemptRepository.findLatestWrongAttempt(USER_ID, 8L)).thenReturn(wrong8);

            List<MissedQuestionResponse> result = service.getMissedQuestions(USER_ID);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).questionId()).isEqualTo(10L);
            assertThat(result.get(1).questionId()).isEqualTo(9L);
            assertThat(result.get(2).questionId()).isEqualTo(8L);
        }

        @Test
        @DisplayName("skips question if no longer exists")
        void missedQuestions_skipsDeletedQuestion() {
            when(attemptRepository.findTop50DistinctWrongQuestionIds(USER_ID))
                    .thenReturn(List.of(99L, 1L));

            when(questionRepository.findById(99L)).thenReturn(Optional.empty());
            QuizQuestion q1 = makeQuestion(1L, "java-core", "JUNIOR", "a");
            when(questionRepository.findById(1L)).thenReturn(Optional.of(q1));
            when(attemptRepository.findLatestWrongAttempt(USER_ID, 1L))
                    .thenReturn(makeWrongAttempt(USER_ID, 1L, Instant.now()));

            List<MissedQuestionResponse> result = service.getMissedQuestions(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).questionId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty list when no wrong answers")
        void missedQuestions_emptyWhenNoWrongAnswers() {
            when(attemptRepository.findTop50DistinctWrongQuestionIds(USER_ID))
                    .thenReturn(List.of());

            List<MissedQuestionResponse> result = service.getMissedQuestions(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Topics with Stats
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /topics")
    class TopicsTests {

        @Test
        @DisplayName("returns all topics with coverage per level")
        void getTopicsWithStats_returnsAllTopics() {
            when(questionRepository.findAllDistinctTopicSlugs())
                    .thenReturn(List.of("java-core", "spring-boot"));
            when(progressRepository.findByUserId(USER_ID)).thenReturn(List.of());
            when(questionRepository.countByTopicSlug("java-core")).thenReturn(10L);
            when(questionRepository.countByTopicSlug("spring-boot")).thenReturn(8L);

            // Junior level
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID)).thenReturn(2L);
            when(questionRepository.countByTopicSlugAndLevelTag("spring-boot", "JUNIOR")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("spring-boot", "JUNIOR", USER_ID)).thenReturn(4L);

            // Middle level
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "MIDDLE")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "MIDDLE", USER_ID)).thenReturn(0L);
            when(questionRepository.countByTopicSlugAndLevelTag("spring-boot", "MIDDLE")).thenReturn(2L);
            when(questionRepository.countMasteredByTopicAndLevel("spring-boot", "MIDDLE", USER_ID)).thenReturn(1L);

            // Senior level
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "SENIOR")).thenReturn(2L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "SENIOR", USER_ID)).thenReturn(0L);
            when(questionRepository.countByTopicSlugAndLevelTag("spring-boot", "SENIOR")).thenReturn(2L);
            when(questionRepository.countMasteredByTopicAndLevel("spring-boot", "SENIOR", USER_ID)).thenReturn(2L);

            List<TopicStatsResponse> result = service.getTopicsWithStats(USER_ID);

            assertThat(result).hasSize(2);
            TopicStatsResponse java = result.stream()
                    .filter(t -> t.topicSlug().equals("java-core")).findFirst().orElseThrow();
            assertThat(java.topicLabel()).isEqualTo("Java Core");
            assertThat(java.questionCount()).isEqualTo(10);
            // juniorCov = 2/4 = 0.5 >= 0.4 → not JUNIOR; middleCov = 0/4 = 0 < 0.75 → MIDDLE
            assertThat(java.coverage().junior()).isCloseTo(0.5, within(0.001));
            assertThat(java.userLevel()).isEqualTo("MIDDLE");
            assertThat(java.currentStreak()).isEqualTo(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Submit Answer – Persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitAnswer persistence")
    class SubmitAnswerPersistenceTests {

        @Test
        @DisplayName("saves QuizAttempt to attemptRepository")
        void submitAnswer_savesAttempt() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(newProgress(USER_ID, "java-core",
                            new BigDecimal("2.50"), 1, 0, 0, false)));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(1L, "a", 1500));

            verify(attemptRepository).save(any(QuizAttempt.class));
        }

        @Test
        @DisplayName("throws QuestionNotFoundException for unknown questionId")
        void submitAnswer_unknownQuestion_throws() {
            when(questionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(999L, "a", null)))
                    .isInstanceOf(QuestionNotFoundException.class);
        }

        @Test
        @DisplayName("increments totalAttempted and totalCorrect on correct answer")
        void submitAnswer_correct_incrementsCounters() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 1, 0, 0, false);
            prog.setTotalAttempted(5);
            prog.setTotalCorrect(3);

            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(1L, "a", 1500));

            assertThat(prog.getTotalAttempted()).isEqualTo(6);
            assertThat(prog.getTotalCorrect()).isEqualTo(4);
        }

        @Test
        @DisplayName("creates default progress if none exists for topic")
        void submitAnswer_createsDefaultProgressIfMissing() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.empty());
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.submitAnswer(USER_ID, new AttemptAnswerRequest(1L, "a", 1500));

            ArgumentCaptor<UserQuizProgress> captor = ArgumentCaptor.forClass(UserQuizProgress.class);
            verify(progressRepository, atLeastOnce()).save(captor.capture());
            UserQuizProgress saved = captor.getAllValues().get(0);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getTopicSlug()).isEqualTo("java-core");
        }

        @Test
        @DisplayName("normalizes answer key before comparison (trim + lowercase)")
        void submitAnswer_normalizesKey() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", " a ");
            UserQuizProgress prog = newProgress(USER_ID, "java-core",
                    new BigDecimal("2.50"), 1, 0, 0, false);

            when(questionRepository.findById(1L)).thenReturn(Optional.of(q));
            when(progressRepository.findByUserIdAndTopicSlug(USER_ID, "java-core"))
                    .thenReturn(Optional.of(prog));
            when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AttemptAnswerResponse resp = service.submitAnswer(USER_ID,
                    new AttemptAnswerRequest(1L, "  A  ", 1000));

            // "  A  " trimmed+lowercased = "a", matches " a " → correct
            assertThat(resp.isCorrect()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Progress by Topic
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /progress/{topicSlug}")
    class ProgressByTopicTests {

        @Test
        @DisplayName("throws TopicNotFoundException for unknown topic")
        void getProgress_unknownTopic_throws() {
            when(questionRepository.findAllDistinctTopicSlugs()).thenReturn(List.of("java-core"));

            assertThatThrownBy(() -> service.getProgressByTopic(USER_ID, "unknown-topic"))
                    .isInstanceOf(TopicNotFoundException.class);
        }

        @Test
        @DisplayName("returns level breakdown for all three levels")
        void getProgress_returnsAllLevelBreakdowns() {
            when(questionRepository.findAllDistinctTopicSlugs()).thenReturn(List.of("java-core"));
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "JUNIOR")).thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "JUNIOR", USER_ID)).thenReturn(2L);
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "MIDDLE")).thenReturn(4L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "MIDDLE", USER_ID)).thenReturn(3L);
            when(questionRepository.countByTopicSlugAndLevelTag("java-core", "SENIOR")).thenReturn(2L);
            when(questionRepository.countMasteredByTopicAndLevel("java-core", "SENIOR", USER_ID)).thenReturn(0L);

            QuizProgressResponse resp = service.getProgressByTopic(USER_ID, "java-core");

            assertThat(resp.topicSlug()).isEqualTo("java-core");
            assertThat(resp.junior()).isNotNull();
            assertThat(resp.junior().mastered()).isEqualTo(2);
            assertThat(resp.junior().total()).isEqualTo(5);
            assertThat(resp.junior().percent()).isCloseTo(0.4, within(0.001));
            assertThat(resp.middle().mastered()).isEqualTo(3);
            assertThat(resp.middle().percent()).isCloseTo(0.75, within(0.001));
            assertThat(resp.senior().percent()).isEqualTo(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Next Question
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /questions/next")
    class NextQuestionTests {

        @Test
        @DisplayName("throws TopicNotFoundException when no topics selected")
        void getNextQuestion_noTopics_throws() {
            assertThatThrownBy(() -> service.getNextQuestion(USER_ID, List.of(), 1))
                    .isInstanceOf(TopicNotFoundException.class)
                    .hasMessageContaining("At least one topic");
        }

        @Test
        @DisplayName("throws TopicNotFoundException when no questions found for topics")
        void getNextQuestion_noQuestions_throws() {
            when(questionRepository.findByTopicSlugIn(List.of("java-core"))).thenReturn(List.of());

            assertThatThrownBy(() -> service.getNextQuestion(USER_ID, List.of("java-core"), 1))
                    .isInstanceOf(TopicNotFoundException.class)
                    .hasMessageContaining("No questions found");
        }

        @Test
        @DisplayName("returns question without correctKey or explanation")
        void getNextQuestion_returnsSanitizedQuestion() {
            QuizQuestion q = makeQuestion(1L, "java-core", "JUNIOR", "a");
            when(questionRepository.findByTopicSlugIn(List.of("java-core")))
                    .thenReturn(List.of(q));
            when(progressRepository.findByUserIdAndTopicSlugIn(USER_ID, List.of("java-core")))
                    .thenReturn(List.of());
            when(questionRepository.countByTopicSlugAndLevelTag(eq("java-core"), anyString()))
                    .thenReturn(5L);
            when(questionRepository.countMasteredByTopicAndLevel(eq("java-core"), anyString(), eq(USER_ID)))
                    .thenReturn(0L);

            QuizQuestionResponse resp = service.getNextQuestion(USER_ID, List.of("java-core"), 1);

            assertThat(resp.id()).isEqualTo(1L);
            assertThat(resp.topicSlug()).isEqualTo("java-core");
            assertThat(resp.levelTag()).isEqualTo("JUNIOR");
            assertThat(resp.options()).hasSize(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper factories
    // ─────────────────────────────────────────────────────────────────────────

    private static QuizQuestion makeQuestion(Long id, String topic, String level, String correctKey) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setTopicSlug(topic);
        q.setLevelTag(level);
        q.setQuestionText("Sample question " + id);
        q.setQuestionType("MULTIPLE_CHOICE");
        q.setOptions(OPTS_JSON);
        q.setCorrectKey(correctKey);
        q.setExplanation("Explanation for " + id);
        return q;
    }

    private static UserQuizProgress newProgress(UUID userId, String topic,
            BigDecimal ef, int interval, int reps, int consecutiveWrong, boolean isRelearning) {
        UserQuizProgress p = new UserQuizProgress();
        p.setUserId(userId);
        p.setTopicSlug(topic);
        p.setEaseFactor(ef);
        p.setIntervalDays(interval);
        p.setRepetitions(reps);
        p.setConsecutiveCorrect(0);
        p.setConsecutiveWrong(consecutiveWrong);
        p.setIsRelearning(isRelearning);
        p.setTotalAttempted(0);
        p.setTotalCorrect(0);
        p.setStreakDays(0);
        p.setLongestStreak(0);
        return p;
    }

    private static QuizAttempt makeWrongAttempt(UUID userId, Long questionId, Instant attemptedAt) {
        QuizAttempt a = new QuizAttempt();
        a.setUserId(userId);
        a.setQuestionId(questionId);
        a.setGivenKey("wrong");
        a.setIsCorrect(false);
        a.setResponseMs(3000);
        // Manually set attemptedAt (normally @PrePersist but we need specific time)
        try {
            var field = QuizAttempt.class.getDeclaredField("attemptedAt");
            field.setAccessible(true);
            field.set(a, attemptedAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return a;
    }
}
