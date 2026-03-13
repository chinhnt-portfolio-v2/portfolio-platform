package dev.chinh.portfolio.platform.metrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectHealthRepository extends JpaRepository<ProjectHealth, UUID> {
    Optional<ProjectHealth> findByProjectSlug(String projectSlug);
}
