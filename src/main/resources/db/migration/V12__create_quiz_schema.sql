-- Quiz Learning App: 3 tables
-- V12: quiz_questions, quiz_attempts, user_quiz_progress

-- ============================================================
-- quiz_questions: question bank, multi-choice stored as JSONB
-- ============================================================
CREATE TABLE quiz_questions (
    id             BIGSERIAL PRIMARY KEY,
    topic_slug     VARCHAR(100) NOT NULL,
    level_tag      VARCHAR(20)  NOT NULL,
    question_text  TEXT         NOT NULL,
    question_type  VARCHAR(20)  NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    options        JSONB,
    correct_key    VARCHAR(20)  NOT NULL,
    explanation    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

ALTER TABLE quiz_questions ADD CONSTRAINT chk_level_tag
    CHECK (level_tag IN ('JUNIOR', 'MIDDLE', 'SENIOR'));
ALTER TABLE quiz_questions ADD CONSTRAINT chk_question_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'MULTIPLE_ANSWER'));

CREATE INDEX idx_questions_topic       ON quiz_questions(topic_slug);
CREATE INDEX idx_questions_level       ON quiz_questions(level_tag);
CREATE INDEX idx_questions_topic_level ON quiz_questions(topic_slug, level_tag);
CREATE INDEX idx_questions_updated     ON quiz_questions(updated_at DESC);

-- ============================================================
-- quiz_attempts: every answer submission, per-user per-question
-- ============================================================
CREATE TABLE quiz_attempts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id  BIGINT    NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    given_key    VARCHAR(20),
    is_correct   BOOLEAN   NOT NULL,
    response_ms  INTEGER,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attempts_user          ON quiz_attempts(user_id);
CREATE INDEX idx_attempts_question      ON quiz_attempts(question_id);
CREATE INDEX idx_attempts_user_question ON quiz_attempts(user_id, question_id);
CREATE INDEX idx_attempts_user_recent   ON quiz_attempts(user_id, attempted_at DESC);

-- ============================================================
-- user_quiz_progress: SM-2 spaced repetition state
-- One row per user per topic_slug
-- ============================================================
CREATE TABLE user_quiz_progress (
    user_id              UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_slug           VARCHAR(100) NOT NULL,
    ease_factor         DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    interval_days        INTEGER      NOT NULL DEFAULT 1,
    repetitions         INTEGER      NOT NULL DEFAULT 0,
    consecutive_correct  INTEGER      NOT NULL DEFAULT 0,
    consecutive_wrong    INTEGER      NOT NULL DEFAULT 0,
    is_relearning        BOOLEAN     NOT NULL DEFAULT FALSE,
    next_review_at       TIMESTAMPTZ,
    total_correct        INTEGER      NOT NULL DEFAULT 0,
    total_attempted      INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at      TIMESTAMPTZ,
    streak_days          INTEGER      NOT NULL DEFAULT 0,
    longest_streak       INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, topic_slug)
);

CREATE INDEX idx_progress_next_review ON user_quiz_progress(next_review_at)
    WHERE next_review_at IS NOT NULL;
CREATE INDEX idx_progress_user_streak  ON user_quiz_progress(user_id, streak_days DESC);

-- ============================================================
-- Trigger: auto-update updated_at on user_quiz_progress
-- ============================================================
CREATE OR REPLACE FUNCTION quiz_progress_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER quiz_progress_updated_at
    BEFORE UPDATE ON user_quiz_progress
    FOR EACH ROW EXECUTE FUNCTION quiz_progress_set_updated_at();
