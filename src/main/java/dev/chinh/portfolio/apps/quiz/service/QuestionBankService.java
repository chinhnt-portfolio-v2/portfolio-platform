package dev.chinh.portfolio.apps.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.apps.quiz.QuizQuestion;
import dev.chinh.portfolio.apps.quiz.QuizQuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);
    private static final String SEED_PATH = "quiz/topics";
    private static final Map<String, String[]> TOPIC_SUBPATHS = Map.of(
            "java-core", new String[]{"java"},
            "spring-boot", new String[]{"spring"},
            "reactjs-ts", new String[]{"react"},
            "javascript", new String[]{"javascript"},
            "css", new String[]{"css"},
            "dsa", new String[]{"dsa"},
            "system-design", new String[]{"system-design"}
    );

    private final QuizQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public QuestionBankService(QuizQuestionRepository questionRepository, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SeedStatus seedQuestionBank() {
        long existing = questionRepository.count();
        log.info("Manual re-seed triggered (current: {} questions). Deleting all and re-seeding...", existing);
        try {
            // Native DELETE bypasses JPA cascade logic — no orphaned entities in persistence context
            entityManager.createNativeQuery("TRUNCATE TABLE quiz_questions RESTART IDENTITY CASCADE").executeUpdate();
            seedIfEmpty();
        } catch (Exception e) {
            log.error("Seed failed: {}", e.getMessage(), e);
            throw new RuntimeException("Seed failed: " + e.getMessage(), e);
        }
        log.info("Manual re-seed complete.");
        return getSeedStatus();
    }

    /**
     * Seed the question bank if the DB is empty.
     * Loads both top-level topic JSONs and all sub-topic JSONs in parallel.
     */
    public void seedIfEmpty() {
        if (questionRepository.count() > 0) {
            log.info("Quiz question bank already seeded ({} questions). Skipping.",
                    questionRepository.count());
            return;
        }

        log.info("Seeding quiz question bank...");
        int total = 0;
        for (String topicSlug : TOPIC_SUBPATHS.keySet()) {
            try {
                int count = loadTopLevel(topicSlug);
                if (count > 0) log.info("  Loaded {} questions from top-level '{}'", count, topicSlug);
                total += count;
            } catch (Exception e) {
                log.warn("  Could not load top-level '{}': {}", topicSlug, e.getMessage());
            }
            String[] subDirs = TOPIC_SUBPATHS.get(topicSlug);
            if (subDirs != null) {
                for (String subDir : subDirs) {
                    try {
                        int subCount = loadSubTopics(subDir, topicSlug);
                        if (subCount > 0) log.info("  Loaded {} questions from sub-dir '{}' (parent: '{}')", subCount, subDir, topicSlug);
                        total += subCount;
                    } catch (Exception e) {
                        log.warn("  Could not load sub-dir '{}': {}", subDir, e.getMessage());
                    }
                }
            }
        }
        log.info("Quiz seed complete: {} total questions.", total);
    }

    private int loadTopLevel(String topicSlug) throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_PATH + "/" + topicSlug + ".json");
        if (!resource.exists()) return 0;
        try (InputStream is = resource.getInputStream()) {
            return parseAndSave(is, topicSlug);
        }
    }

    private int loadSubTopics(String subDir, String parentSlug) throws IOException {
        ClassPathResource dirResource = new ClassPathResource(SEED_PATH + "/" + subDir);
        if (!dirResource.exists()) return 0;

        int saved = 0;
        // Walk through known sub-topic JSON filenames per sub-directory
        String[] knownFiles = subDir.equals("java") ? new String[]{
                    "java-collections.json", "java-oop.json"} :
                    subDir.equals("spring") ? new String[]{
                            "spring-boot-auto.json", "spring-di.json", "spring-jpa.json",
                            "spring-mvc.json", "spring-security.json", "spring-testing.json", "spring-transactions.json"} :
                    subDir.equals("react") ? new String[]{
                            "react-components.json", "react-ecosystem.json", "react-hooks.json",
                            "react-perf.json", "react-rendering.json", "react-state.json", "react-ts.json"} :
                    subDir.equals("javascript") ? new String[]{
                            "js-async.json", "js-dom.json", "js-es6.json", "js-functions.json",
                            "js-fundamentals.json", "js-modules.json", "js-prototype.json"} :
                    subDir.equals("css") ? new String[]{
                            "css-advanced.json", "css-animation.json", "css-flexbox.json",
                            "css-fundamentals.json", "css-grid.json", "css-responsive.json", "css-typography.json"} :
                    subDir.equals("dsa") ? new String[]{
                            "dsa-arrays.json", "dsa-dp.json", "dsa-hash.json", "dsa-linked-lists.json",
                            "dsa-sorting.json", "dsa-stacks-queues.json", "dsa-trees.json"} :
                    subDir.equals("system-design") ? new String[]{
                            "sd-caching.json", "sd-databases.json", "sd-messaging.json",
                            "sd-microservices.json", "sd-scalability.json", "sd-security.json"} :
                    new String[]{};

            for (String file : knownFiles) {
                ClassPathResource fileRes = new ClassPathResource(SEED_PATH + "/" + subDir + "/" + file);
                if (fileRes.exists()) {
                    try (InputStream fis = fileRes.getInputStream()) {
                        saved += parseAndSave(fis, parentSlug);
                    } catch (Exception e) {
                        log.warn("    Failed to parse '{}': {}", file, e.getMessage());
                    }
                }
            }
        return saved;
    }

    private int parseAndSave(InputStream is, String topicSlug) throws IOException {
        JsonNode root = objectMapper.readTree(is);
        JsonNode questionsNode = root.get("questions");
        if (questionsNode == null || !questionsNode.isArray()) return 0;

        int saved = 0;
        for (JsonNode qNode : questionsNode) {
            QuizQuestion q = new QuizQuestion();
            q.setTopicSlug(topicSlug);
            q.setLevelTag(qNode.has("levelTag") ? qNode.get("levelTag").asText() : "JUNIOR");
            q.setQuestionText(qNode.has("questionText") ? qNode.get("questionText").asText() : "");
            q.setQuestionType(qNode.has("questionType") ? qNode.get("questionType").asText() : "MULTIPLE_CHOICE");
            if (qNode.has("options")) {
                List<Option> opts = new ArrayList<>();
                for (JsonNode o : qNode.get("options")) {
                    opts.add(new Option(
                            o.has("id") ? o.get("id").asText() : "",
                            o.has("text") ? o.get("text").asText() : ""));
                }
                q.setOptions(objectMapper.writeValueAsString(opts));
            }
            q.setCorrectKey(qNode.has("correctKey") ? qNode.get("correctKey").asText() : "");
            q.setExplanation(qNode.has("explanation") ? qNode.get("explanation").asText() : null);
            questionRepository.save(q);
            saved++;
        }
        return saved;
    }

    public SeedStatus getSeedStatus() {
        long count = questionRepository.count();
        return new SeedStatus(count > 0, 7, (int) count);
    }

    public record SeedStatus(boolean seeded, int topicCount, int questionCount) {}
    public record Option(String id, String text) {}
}
