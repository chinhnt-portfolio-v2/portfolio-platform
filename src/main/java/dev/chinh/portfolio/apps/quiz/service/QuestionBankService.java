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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionBankService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankService.class);
    private static final String SEED_PATH = "quiz/topics";

    private final QuizQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public QuestionBankService(QuizQuestionRepository questionRepository, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Seed the question bank if the DB is empty.
     * Called via ApplicationRunner after Flyway migrations run.
     */
    @Transactional
    public void seedIfEmpty() {
        if (questionRepository.count() > 0) {
            log.info("Quiz question bank already seeded ({} questions). Skipping.",
                    questionRepository.count());
            return;
        }

        log.info("Seeding quiz question bank...");
        String[] topics = {"java-core", "spring-boot", "reactjs-ts",
                           "javascript", "css", "dsa", "system-design"};
        int total = 0;
        for (String topic : topics) {
            try {
                int count = loadTopic(topic);
                if (count > 0) {
                    log.info("  Loaded {} questions for topic '{}'", count, topic);
                    total += count;
                }
            } catch (Exception e) {
                log.warn("  Could not load topic '{}': {}", topic, e.getMessage());
            }
        }
        log.info("Quiz seed complete: {} total questions.", total);
    }

    private int loadTopic(String topicSlug) throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_PATH + "/" + topicSlug + ".json");
        if (!resource.exists()) return 0;

        try (InputStream is = resource.getInputStream()) {
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
    }

    public SeedStatus getSeedStatus() {
        long count = questionRepository.count();
        return new SeedStatus(count > 0, 7, (int) count);
    }

    public record SeedStatus(boolean seeded, int topicCount, int questionCount) {}
    public record Option(String id, String text) {}
}
