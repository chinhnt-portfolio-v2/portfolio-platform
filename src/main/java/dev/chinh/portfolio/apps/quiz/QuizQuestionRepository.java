package dev.chinh.portfolio.apps.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByTopicSlug(String topicSlug);

    List<QuizQuestion> findByTopicSlugIn(List<String> topicSlugs);

    List<QuizQuestion> findByTopicSlugAndLevelTag(String topicSlug, String levelTag);

    List<QuizQuestion> findByTopicSlugInAndLevelTag(List<String> topicSlugs, String levelTag);

    long countByTopicSlug(String topicSlug);

    long countByTopicSlugAndLevelTag(String topicSlug, String levelTag);

    @Query("SELECT COUNT(q) FROM QuizQuestion q WHERE q.topicSlug = :topicSlug AND q.levelTag = :levelTag AND q.id IN (" +
           "SELECT pa.questionId FROM QuizAttempt pa WHERE pa.userId = :userId AND pa.isCorrect = true " +
           "GROUP BY pa.questionId HAVING COUNT(pa) >= 2)")
    long countMasteredByTopicAndLevel(
        @Param("topicSlug") String topicSlug,
        @Param("levelTag") String levelTag,
        @Param("userId") java.util.UUID userId
    );

    @Query("SELECT DISTINCT q.topicSlug FROM QuizQuestion q")
    List<String> findAllDistinctTopicSlugs();
}
