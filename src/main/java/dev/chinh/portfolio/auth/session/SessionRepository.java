package dev.chinh.portfolio.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByRefreshToken(String refreshToken);
    List<Session> findAllByUserIdAndRevokedFalse(UUID userId);

    @Modifying
    void deleteByUserId(UUID userId);
}
