package dev.chinh.portfolio.apps.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuizProgressRepository extends JpaRepository<UserQuizProgress, UserQuizProgressId> {

    Optional<UserQuizProgress> findByUserIdAndTopicSlug(UUID userId, String topicSlug);

    List<UserQuizProgress> findByUserId(UUID userId);

    @Query("SELECT p FROM UserQuizProgress p WHERE p.userId = :userId AND p.topicSlug IN :topicSlugs")
    List<UserQuizProgress> findByUserIdAndTopicSlugIn(
        @Param("userId") UUID userId,
        @Param("topicSlugs") List<String> topicSlugs
    );

    void deleteByUserIdAndTopicSlug(UUID userId, String topicSlug);
}
