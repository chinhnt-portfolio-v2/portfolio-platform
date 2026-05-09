package dev.chinh.portfolio.apps.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * Fetch the most recent attempt for a given user + question.
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.userId = :userId AND a.questionId = :questionId ORDER BY a.attemptedAt DESC LIMIT 1")
    QuizAttempt findLatestByUserIdAndQuestionId(
        @Param("userId") UUID userId,
        @Param("questionId") Long questionId
    );

    /**
     * Top 50 distinct wrong attempts, most recent first.
     */
    @Query("""
        SELECT DISTINCT a.questionId FROM QuizAttempt a
        WHERE a.userId = :userId AND a.isCorrect = false
        ORDER BY a.attemptedAt DESC
        LIMIT 50
        """)
    List<Long> findTop50DistinctWrongQuestionIds(@Param("userId") UUID userId);

    /**
     * Latest wrong attempt for a specific question.
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.userId = :userId AND a.questionId = :questionId AND a.isCorrect = false ORDER BY a.attemptedAt DESC LIMIT 1")
    QuizAttempt findLatestWrongAttempt(
        @Param("userId") UUID userId,
        @Param("questionId") Long questionId
    );

    /**
     * How many times has this user answered this question correctly?
     */
    long countByUserIdAndQuestionIdAndIsCorrectTrue(UUID userId, Long questionId);

    /**
     * How many times has this user attempted this question (any result)?
     */
    long countByUserIdAndQuestionId(UUID userId, Long questionId);

    /**
     * All question IDs this user has ever attempted (any result).
     */
    @Query("SELECT a.questionId FROM QuizAttempt a WHERE a.userId = :userId")
    List<Long> findAttemptedQuestionIds(@Param("userId") UUID userId);

    /**
     * Paginated attempt history with optional filters.
     */
    @Query("SELECT a FROM QuizAttempt a WHERE a.userId = :userId " +
           "AND (:topic IS NULL OR :topic = '' OR a.questionId IN (SELECT q.id FROM QuizQuestion q WHERE q.topicSlug = :topic)) " +
           "AND (:isCorrect IS NULL OR a.isCorrect = :isCorrect) " +
           "AND (:from IS NULL OR a.attemptedAt >= :from) " +
           "AND (:to IS NULL OR a.attemptedAt <= :to) " +
           "ORDER BY a.attemptedAt DESC")
    Page<QuizAttempt> findByUserIdWithFilters(
        @Param("userId") UUID userId,
        @Param("topic") String topic,
        @Param("isCorrect") Boolean isCorrect,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable);

    /**
     * Delete all attempts for a user within a specific topic.
     */
    @Modifying
    @Query("DELETE FROM QuizAttempt a WHERE a.userId = :userId AND a.questionId IN (SELECT q.id FROM QuizQuestion q WHERE q.topicSlug = :topicSlug)")
    void deleteByUserIdAndTopicSlug(@Param("userId") UUID userId, @Param("topicSlug") String topicSlug);
}
