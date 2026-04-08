-- V14: Guard constraints for quiz_questions (safety net after removing broken V13)
-- V12 created the table with NOT NULL on topic_slug; V13 was entirely NULL placeholders.
-- This migration explicitly re-applies constraints idempotently.
-- The @PostConstruct QuizSeedRunner (via QuestionBankService JSON) handles seeding.

-- Re-assert NOT NULL on topic_slug (idempotent: no-op if already enforced)
ALTER TABLE quiz_questions ALTER COLUMN topic_slug SET NOT NULL;

-- Re-assert CHECK constraints (idempotent: no-op if already exist)
ALTER TABLE quiz_questions DROP CONSTRAINT IF EXISTS chk_level_tag;
ALTER TABLE quiz_questions ADD CONSTRAINT chk_level_tag
    CHECK (level_tag IN ('JUNIOR', 'MIDDLE', 'SENIOR'));

ALTER TABLE quiz_questions DROP CONSTRAINT IF EXISTS chk_question_type;
ALTER TABLE quiz_questions ADD CONSTRAINT chk_question_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'MULTIPLE_ANSWER', 'CODE_OUTPUT'));
