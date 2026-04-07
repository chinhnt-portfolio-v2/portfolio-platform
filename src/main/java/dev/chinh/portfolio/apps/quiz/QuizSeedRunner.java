package dev.chinh.portfolio.apps.quiz;

import dev.chinh.portfolio.apps.quiz.service.QuestionBankService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

/**
 * Triggers question bank seeding once on application startup,
 * after Flyway migrations have been applied.
 */
@Component
public class QuizSeedRunner {

    private final QuestionBankService questionBankService;

    public QuizSeedRunner(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @PostConstruct
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seed() {
        questionBankService.seedIfEmpty();
    }
}
