package dev.chinh.portfolio.apps.quiz;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_slug", nullable = false, length = 100)
    private String topicSlug;

    @Column(name = "level_tag", nullable = false, length = 20)
    private String levelTag; // JUNIOR | MIDDLE | SENIOR

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType = "MULTIPLE_CHOICE"; // MULTIPLE_CHOICE | TRUE_FALSE | MULTIPLE_ANSWER

    @Column(columnDefinition = "jsonb")
    private String options; // JSON array: [{"id":"a","text":"..."}]

    @Column(name = "correct_key", nullable = false, length = 20)
    private String correctKey;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "lang", nullable = false, length = 2)
    private String lang = "en";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopicSlug() { return topicSlug; }
    public void setTopicSlug(String topicSlug) { this.topicSlug = topicSlug; }

    public String getLevelTag() { return levelTag; }
    public void setLevelTag(String levelTag) { this.levelTag = levelTag; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getCorrectKey() { return correctKey; }
    public void setCorrectKey(String correctKey) { this.correctKey = correctKey; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
}
