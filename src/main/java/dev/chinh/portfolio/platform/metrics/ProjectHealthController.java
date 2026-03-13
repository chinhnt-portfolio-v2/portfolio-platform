package dev.chinh.portfolio.platform.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/project-health")
public class ProjectHealthController {

    private final ProjectHealthRepository repository;
    private final MetricsMapper mapper;

    public ProjectHealthController(ProjectHealthRepository repository, MetricsMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ProjectHealthDto> getAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }
}
