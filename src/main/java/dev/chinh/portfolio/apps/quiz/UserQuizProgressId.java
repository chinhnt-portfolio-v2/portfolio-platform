package dev.chinh.portfolio.apps.quiz;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link UserQuizProgress}.
 * Used with {@link jakarta.persistence.IdClass}.
 */
public class UserQuizProgressId implements Serializable {

    private UUID userId;
    private String topicSlug;

    public UserQuizProgressId() {}

    public UserQuizProgressId(UUID userId, String topicSlug) {
        this.userId = userId;
        this.topicSlug = topicSlug;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getTopicSlug() { return topicSlug; }
    public void setTopicSlug(String topicSlug) { this.topicSlug = topicSlug; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserQuizProgressId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(topicSlug, that.topicSlug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, topicSlug);
    }
}
