package dev.chinh.portfolio.apps.quiz;

import dev.chinh.portfolio.apps.quiz.service.QuestionBankService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triggers question bank seeding once on application startup.
 * Uses ApplicationRunner (not @PostConstruct) so the embedded HTTP server
 * starts and health checks pass BEFORE potentially slow seeding runs.
 */
@Component
public class QuizSeedRunner implements ApplicationRunner {

    private final QuestionBankService questionBankService;

    public QuizSeedRunner(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        questionBankService.seedIfEmpty();
    }
}
