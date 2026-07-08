package dev.chinh.portfolio.apps.quiz;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_quiz_progress")
@IdClass(UserQuizProgressId.class)
public class UserQuizProgress {

    @Id
    @Column(name = "user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID userId;

    @Id
    @Column(name = "topic_slug", nullable = false, length = 100)
    private String topicSlug;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor = new BigDecimal("2.50");

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 1;

    @Column(nullable = false)
    private Integer repetitions = 0;

    @Column(name = "consecutive_correct", nullable = false)
    private Integer consecutiveCorrect = 0;

    @Column(name = "consecutive_wrong", nullable = false)
    private Integer consecutiveWrong = 0;

    @Column(name = "is_relearning", nullable = false)
    private Boolean isRelearning = false;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "total_correct", nullable = false)
    private Integer totalCorrect = 0;

    @Column(name = "total_attempted", nullable = false)
    private Integer totalAttempted = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "streak_days", nullable = false)
    private Integer streakDays = 0;

    @Column(name = "longest_streak", nullable = false)
    private Integer longestStreak = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getTopicSlug() { return topicSlug; }
    public void setTopicSlug(String topicSlug) { this.topicSlug = topicSlug; }

    public BigDecimal getEaseFactor() { return easeFactor; }
    public void setEaseFactor(BigDecimal easeFactor) { this.easeFactor = easeFactor; }

    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }

    public Integer getRepetitions() { return repetitions; }
    public void setRepetitions(Integer repetitions) { this.repetitions = repetitions; }

    public Integer getConsecutiveCorrect() { return consecutiveCorrect; }
    public void setConsecutiveCorrect(Integer consecutiveCorrect) { this.consecutiveCorrect = consecutiveCorrect; }

    public Integer getConsecutiveWrong() { return consecutiveWrong; }
    public void setConsecutiveWrong(Integer consecutiveWrong) { this.consecutiveWrong = consecutiveWrong; }

    public Boolean getIsRelearning() { return isRelearning; }
    public void setIsRelearning(Boolean isRelearning) { this.isRelearning = isRelearning; }

    public Instant getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(Instant nextReviewAt) { this.nextReviewAt = nextReviewAt; }

    public Integer getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(Integer totalCorrect) { this.totalCorrect = totalCorrect; }

    public Integer getTotalAttempted() { return totalAttempted; }
    public void setTotalAttempted(Integer totalAttempted) { this.totalAttempted = totalAttempted; }

    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }

    public Integer getLongestStreak() { return longestStreak; }
    public void setLongestStreak(Integer longestStreak) { this.longestStreak = longestStreak; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
