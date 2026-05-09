package dev.chinh.portfolio.apps.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.apps.quiz.*;
import dev.chinh.portfolio.apps.quiz.dto.*;
import dev.chinh.portfolio.apps.quiz.exception.QuestionNotFoundException;
import dev.chinh.portfolio.apps.quiz.exception.TopicNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private static final double MIDDLE_THRESHOLD = 0.40;
    private static final double SENIOR_THRESHOLD = 0.75;
    private static final double WEIGHT_JUNIOR = 3.0;
    private static final double WEIGHT_MIDDLE = 1.5;
    private static final double WEIGHT_SENIOR = 1.0;

    private static final Map<String, String> TOPIC_LABELS = Map.ofEntries(
            Map.entry("java-core",      "Java Core"),
            Map.entry("spring-boot",    "Spring Boot"),
            Map.entry("reactjs-ts",     "ReactJS / TypeScript"),
            Map.entry("javascript",     "JavaScript"),
            Map.entry("css",           "CSS"),
            Map.entry("dsa",           "DSA"),
            Map.entry("system-design", "System Design")
    );

    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository  attemptRepository;
    private final UserQuizProgressRepository progressRepository;
    private final ObjectMapper objectMapper;

    public QuizService(
            QuizQuestionRepository questionRepository,
            QuizAttemptRepository  attemptRepository,
            UserQuizProgressRepository progressRepository,
            ObjectMapper objectMapper) {
        this.questionRepository   = questionRepository;
        this.attemptRepository    = attemptRepository;
        this.progressRepository   = progressRepository;
        this.objectMapper         = objectMapper;
    }

    // ── GET /topics ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TopicStatsResponse> getTopicsWithStats(UUID userId) {
        List<String> allTopics = questionRepository.findAllDistinctTopicSlugs();
        Map<String, UserQuizProgress> progressMap = progressRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserQuizProgress::getTopicSlug, p -> p));

        List<TopicStatsResponse> result = new ArrayList<>();
        for (String topicSlug : allTopics) {
            long total = questionRepository.countByTopicSlug(topicSlug);
            double junCov = coverage(userId, topicSlug, "JUNIOR");
            double midCov = coverage(userId, topicSlug, "MIDDLE");
            double senCov = coverage(userId, topicSlug, "SENIOR");
            String userLevel = deriveLevel(junCov, midCov, senCov);
            int streak = Optional.ofNullable(progressMap.get(topicSlug))
                    .map(UserQuizProgress::getStreakDays).orElse(0);
            result.add(new TopicStatsResponse(
                    topicSlug,
                    TOPIC_LABELS.getOrDefault(topicSlug, topicSlug),
                    total,
                    new TopicStatsResponse.Coverage(junCov, midCov, senCov),
                    userLevel,
                    streak
            ));
        }
        return result;
    }

    // ── GET /questions/next ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public QuizQuestionResponse getNextQuestion(UUID userId, List<String> topicSlugs,
                                                  int limit, List<Long> exclude,
                                                  HttpServletRequest request) {
        String lang = (request != null) ? detectLang(request) : null;
        return getNextQuestionInternal(userId, topicSlugs, limit, exclude, null, lang);
    }

    // ── POST /attempts ───────────────────────────────────────────────────────
    @Transactional
    public AttemptAnswerResponse submitAnswer(UUID userId, AttemptAnswerRequest req) {
        QuizQuestion question = questionRepository.findById(req.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(req.questionId()));

        boolean isCorrect = normalizeKey(req.givenKey())
                .equals(normalizeKey(question.getCorrectKey()));

        // Persist attempt
        QuizAttempt attempt = new QuizAttempt();
        attempt.setUserId(userId);
        attempt.setQuestionId(question.getId());
        attempt.setGivenKey(req.givenKey());
        attempt.setIsCorrect(isCorrect);
        attempt.setResponseMs(req.responseMs());
        attemptRepository.save(attempt);

        // Load or create progress row for this topic
        UserQuizProgress prog = progressRepository
                .findByUserIdAndTopicSlug(userId, question.getTopicSlug())
                .orElseGet(() -> createDefaultProgress(userId, question.getTopicSlug()));

        int preLapseInterval = prog.getIntervalDays();

        if (isCorrect) {
            handleCorrect(prog, preLapseInterval);
        } else {
            handleWrong(prog);
        }

        updateStreak(prog);
        prog.setLastAttemptAt(Instant.now());
        prog.setTotalAttempted(prog.getTotalAttempted() + 1);
        if (isCorrect) prog.setTotalCorrect(prog.getTotalCorrect() + 1);
        prog.setNextReviewAt(Instant.now().plus(Duration.ofDays(prog.getIntervalDays())));
        progressRepository.save(prog);

        return new AttemptAnswerResponse(
                isCorrect,
                question.getCorrectKey(),
                question.getExplanation(),
                toSRSessionState(prog),
                prog.getStreakDays()
        );
    }

    // ── GET /attempts/missed ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MissedQuestionResponse> getMissedQuestions(UUID userId) {
        List<Long> wrongIds = attemptRepository.findTop50DistinctWrongQuestionIds(userId);
        List<MissedQuestionResponse> result = new ArrayList<>();
        for (Long questionId : wrongIds) {
            QuizQuestion q = questionRepository.findById(questionId).orElse(null);
            if (q == null) continue;
            QuizAttempt wrongAttempt = attemptRepository.findLatestWrongAttempt(userId, questionId);
            if (wrongAttempt == null) continue;
            result.add(new MissedQuestionResponse(
                    q.getId(), q.getTopicSlug(), q.getLevelTag(),
                    q.getQuestionText(),
                    wrongAttempt.getGivenKey(), q.getCorrectKey(),
                    q.getExplanation(), wrongAttempt.getAttemptedAt()
            ));
        }
        return result;
    }

    // ── GET /attempts (paginated history) ──────────────────────────────────
    @Transactional(readOnly = true)
    public Page<AttemptHistoryResponse> getAttemptHistory(
            UUID userId, String topic, Boolean isCorrect,
            Instant from, Instant to, Pageable pageable) {
        Page<QuizAttempt> attempts = attemptRepository.findByUserIdWithFilters(
                userId, topic, isCorrect, from, to, pageable);
        return attempts.map(a -> {
            QuizQuestion q = questionRepository.findById(a.getQuestionId()).orElse(null);
            if (q == null) {
                return new AttemptHistoryResponse(
                        null, null, null, null,
                        a.getGivenKey(), null,
                        false, a.getResponseMs(), a.getAttemptedAt());
            }
            return new AttemptHistoryResponse(
                    q.getId(),
                    q.getTopicSlug(),
                    q.getLevelTag(),
                    q.getQuestionText(),
                    a.getGivenKey(),
                    q.getCorrectKey(),
                    Boolean.TRUE.equals(a.getIsCorrect()),
                    a.getResponseMs(),
                    a.getAttemptedAt()
            );
        });
    }

    // ── DELETE /progress/:topicSlug ─────────────────────────────────────────
    @Transactional
    public void resetProgress(UUID userId, String topicSlug) {
        attemptRepository.deleteByUserIdAndTopicSlug(userId, topicSlug);
        progressRepository.deleteByUserIdAndTopicSlug(userId, topicSlug);
    }

    // ── GET /questions/next (with optional level filter) ──────────────────
    @Transactional(readOnly = true)
    public QuizQuestionResponse getNextQuestion(
            UUID userId, List<String> topicSlugs, int limit,
            List<Long> exclude, String level) {
        return getNextQuestionInternal(userId, topicSlugs, limit, exclude, level, null);
    }

    private QuizQuestionResponse getNextQuestionInternal(
            UUID userId, List<String> topicSlugs, int limit,
            List<Long> exclude, String level, String lang) {
        if (topicSlugs == null || topicSlugs.isEmpty()) {
            throw new TopicNotFoundException("At least one topic must be selected");
        }

        Map<String, String> levels = new HashMap<>();
        for (String slug : topicSlugs) {
            levels.put(slug, deriveLevel(
                    coverage(userId, slug, "JUNIOR"),
                    coverage(userId, slug, "MIDDLE"),
                    coverage(userId, slug, "SENIOR")));
        }

        Set<Long> excludedIds = (exclude != null) ? new HashSet<>(exclude) : Collections.emptySet();

        List<QuizQuestion> questions;
        if (level != null && !level.isBlank()) {
            questions = questionRepository.findByTopicSlugInAndLevelTag(topicSlugs, level);
        } else if (lang != null && !lang.isBlank()) {
            questions = questionRepository.findByTopicSlugInAndLang(topicSlugs, lang);
        } else {
            questions = questionRepository.findByTopicSlugIn(topicSlugs);
        }
        if (questions.isEmpty()) {
            throw new TopicNotFoundException("No questions found for topics: " + topicSlugs);
        }

        Set<Long> attemptedIds = new HashSet<>(attemptRepository.findAttemptedQuestionIds(userId));

        Map<String, UserQuizProgress> topicProg = progressRepository
                .findByUserIdAndTopicSlugIn(userId, topicSlugs).stream()
                .collect(Collectors.toMap(UserQuizProgress::getTopicSlug, p -> p));

        List<WeightedQuestion> weighted = new ArrayList<>();
        for (QuizQuestion q : questions) {
            if (excludedIds.contains(q.getId())) continue;
            UserQuizProgress prog = topicProg.get(q.getTopicSlug());
            double w = computeWeight(q, prog, levels.get(q.getTopicSlug()), attemptedIds.contains(q.getId()));
            weighted.add(new WeightedQuestion(q, w));
        }

        weighted.sort((a, b) -> Double.compare(b.weight, a.weight));
        List<QuizQuestion> selected = weighted.stream().limit(limit).map(wq -> wq.question).toList();

        if (selected.isEmpty()) throw new TopicNotFoundException("No questions available for the selected topics");
        return toResponse(selected.getFirst());
    }

    // ── GET /progress/:topicSlug ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public QuizProgressResponse getProgressByTopic(UUID userId, String topicSlug) {
        ensureTopicExists(topicSlug);
        String userLevel = deriveLevel(
                coverage(userId, topicSlug, "JUNIOR"),
                coverage(userId, topicSlug, "MIDDLE"),
                coverage(userId, topicSlug, "SENIOR"));
        return new QuizProgressResponse(
                topicSlug, userLevel,
                levelBreakdown(userId, topicSlug, "JUNIOR"),
                levelBreakdown(userId, topicSlug, "MIDDLE"),
                levelBreakdown(userId, topicSlug, "SENIOR")
        );
    }

    // ── SM-2 State Machine ───────────────────────────────────────────────────

    private void handleCorrect(UserQuizProgress prog, int preLapseInterval) {
        if (prog.getIsRelearning()) {
            prog.setIntervalDays(Math.max(1, preLapseInterval / 2));
            prog.setIsRelearning(false);
            prog.setConsecutiveWrong(0);
            prog.setEaseFactor(maxEF(prog.getEaseFactor().subtract(new BigDecimal("0.15"))));
        } else {
            prog.setConsecutiveCorrect(prog.getConsecutiveCorrect() + 1);
            int reps = prog.getRepetitions();
            if (reps == 0) {
                prog.setIntervalDays(1);
                prog.setRepetitions(1);
            } else if (reps == 1) {
                prog.setIntervalDays(6);
                prog.setRepetitions(2);
            } else {
                BigDecimal newInterval = BigDecimal.valueOf(prog.getIntervalDays())
                        .multiply(prog.getEaseFactor())
                        .setScale(0, RoundingMode.HALF_UP);
                prog.setIntervalDays(Math.max(1, newInterval.intValue()));
                prog.setRepetitions(reps + 1);
            }
        }
        // SM-2 EF increase on correct
        prog.setEaseFactor(prog.getEaseFactor().add(new BigDecimal("0.1")));
        // reset consecutive_correct streak reset is handled in handleWrong
    }

    private void handleWrong(UserQuizProgress prog) {
        prog.setConsecutiveWrong(prog.getConsecutiveWrong() + 1);
        prog.setRepetitions(Math.max(0, prog.getRepetitions() - 1));
        prog.setIntervalDays(Math.max(1, prog.getIntervalDays() / 2));
        prog.setIsRelearning(true);
        prog.setConsecutiveCorrect(0);
        prog.setEaseFactor(maxEF(prog.getEaseFactor().subtract(new BigDecimal("0.2"))));
    }

    // ── Streak Tracking ───────────────────────────────────────────────────────

    private void updateStreak(UserQuizProgress prog) {
        ZoneId zone = ZoneId.of("Asia/Bangkok");
        LocalDate today = LocalDate.now(zone);
        LocalDate lastDay = (prog.getLastAttemptAt() != null)
                ? prog.getLastAttemptAt().atZone(zone).toLocalDate()
                : null;

        if (lastDay == null) {
            prog.setStreakDays(1);
        } else if (!lastDay.equals(today) && !lastDay.equals(today.minusDays(1))) {
            prog.setStreakDays(1);
        } else if (lastDay.equals(today.minusDays(1))) {
            prog.setStreakDays(prog.getStreakDays() + 1);
            prog.setLongestStreak(Math.max(prog.getLongestStreak(), prog.getStreakDays()));
        }
        // Same-day attempt: no change
    }

    // ── Coverage & Level ─────────────────────────────────────────────────────

    private double coverage(UUID userId, String topicSlug, String levelTag) {
        long total = questionRepository.countByTopicSlugAndLevelTag(topicSlug, levelTag);
        if (total == 0) return 0.0;
        long mastered = questionRepository.countMasteredByTopicAndLevel(topicSlug, levelTag, userId);
        return (double) mastered / total;
    }

    private QuizProgressResponse.LevelBreakdown levelBreakdown(UUID userId, String topicSlug, String levelTag) {
        long total = questionRepository.countByTopicSlugAndLevelTag(topicSlug, levelTag);
        long mastered = questionRepository.countMasteredByTopicAndLevel(topicSlug, levelTag, userId);
        double pct = total > 0 ? (double) mastered / total : 0.0;
        return new QuizProgressResponse.LevelBreakdown((int) mastered, (int) total, pct);
    }

    private String deriveLevel(double junCov, double midCov, double senCov) {
        if (junCov < MIDDLE_THRESHOLD) return "JUNIOR";
        if (midCov < SENIOR_THRESHOLD) return "MIDDLE";
        return "SENIOR";
    }

    // ── Question Selection ───────────────────────────────────────────────────

    /** Never-seen questions get this bonus — always served before any repeated question. */
    private static final double NEVER_SEEN_BONUS = 1000.0;

    private double computeWeight(QuizQuestion q, UserQuizProgress prog, String userLevel, boolean hasBeenAttempted) {
        // Never-seen questions always get maximum priority — they appear before any repeat.
        if (!hasBeenAttempted) return NEVER_SEEN_BONUS;

        double overdueDays;
        if (prog == null || prog.getNextReviewAt() == null) {
            overdueDays = 30; // No SRS data
        } else {
            overdueDays = Math.max(0,
                    Duration.between(prog.getNextReviewAt(), Instant.now()).toDays());
        }

        int consecutiveWrong = (prog != null) ? prog.getConsecutiveWrong() : 0;
        double wrongFactor = Math.pow(2, Math.min(consecutiveWrong, 5));

        double ef = (prog != null) ? prog.getEaseFactor().doubleValue() : 2.5;
        double efFactor = 2.5 / Math.max(1.3, ef);

        double levelBonus = switch (userLevel) {
            case "JUNIOR" -> WEIGHT_JUNIOR;
            case "MIDDLE" -> WEIGHT_MIDDLE;
            default       -> WEIGHT_SENIOR;
        };

        return (overdueDays + 1) * wrongFactor * efFactor * levelBonus;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private QuizQuestionResponse toResponse(QuizQuestion q) {
        List<QuizQuestionResponse.QuizOption> opts = parseOptions(q.getOptions());
        return new QuizQuestionResponse(
                q.getId(), q.getTopicSlug(), q.getLevelTag(),
                q.getQuestionText(), q.getQuestionType(), opts);
    }

    private List<QuizQuestionResponse.QuizOption> parseOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<QuizQuestionResponse.QuizOption>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse options JSON: {}", json);
            return List.of();
        }
    }

    private SRSessionState toSRSessionState(UserQuizProgress prog) {
        return new SRSessionState(
                prog.getEaseFactor(), prog.getIntervalDays(), prog.getRepetitions(),
                prog.getConsecutiveCorrect(), prog.getConsecutiveWrong(),
                prog.getIsRelearning(), prog.getNextReviewAt()
        );
    }

    private UserQuizProgress createDefaultProgress(UUID userId, String topicSlug) {
        UserQuizProgress p = new UserQuizProgress();
        p.setUserId(userId);
        p.setTopicSlug(topicSlug);
        return p;
    }

    private String normalizeKey(String key) {
        return (key != null) ? key.trim().toLowerCase() : "";
    }

    private BigDecimal maxEF(BigDecimal val) {
        return val.max(new BigDecimal("1.3"));
    }

    private void ensureTopicExists(String topicSlug) {
        if (!questionRepository.findAllDistinctTopicSlugs().contains(topicSlug))
            throw new TopicNotFoundException(topicSlug);
    }

    /** Parses Accept-Language header, returns primary language tag or "en" as default. */
    private String detectLang(HttpServletRequest request) {
        if (request == null) return "en";
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) return "en";
        // e.g. "vi-VN,en-US;q=0.9" → take first token before ';'
        String primary = header.split(",")[0].split(";")[0].trim();
        // e.g. "vi-VN" → take first part
        if (primary.contains("-")) {
            primary = primary.split("-")[0];
        }
        if (primary.length() == 2) return primary;
        return "en";
    }

    private record WeightedQuestion(QuizQuestion question, double weight) {}
}
