package dev.chinh.portfolio.apps.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
}
