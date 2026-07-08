package dev.chinh.portfolio.apps.quiz;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "given_key", length = 20)
    private String givenKey;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "response_ms")
    private Integer responseMs;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() {
        attemptedAt = Instant.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getGivenKey() { return givenKey; }
    public void setGivenKey(String givenKey) { this.givenKey = givenKey; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

    public Integer getResponseMs() { return responseMs; }
    public void setResponseMs(Integer responseMs) { this.responseMs = responseMs; }

    public Instant getAttemptedAt() { return attemptedAt; }
}
