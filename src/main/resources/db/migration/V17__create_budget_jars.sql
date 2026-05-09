-- V17__create_budget_jars.sql
-- Adds percentage-based budget jars (6-jar JARS method)

CREATE TABLE budget_jars (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    percentage  DECIMAL(5,2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    icon        VARCHAR(50)  DEFAULT '🏺',
    color       VARCHAR(7)   DEFAULT '#0EA5E9',
    is_preset   BOOLEAN      DEFAULT FALSE,
    sort_order  INTEGER      DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Trigger: enforce SUM(percentage) <= 100 per user (prevents race condition)
CREATE OR REPLACE FUNCTION check_jar_percentage_sum()
RETURNS TRIGGER AS $$
DECLARE total DECIMAL(5,2);
BEGIN
    SELECT COALESCE(SUM(percentage), 0) INTO total
    FROM budget_jars
    WHERE user_id = NEW.user_id AND id != COALESCE(NEW.id, -1);
    IF total + NEW.percentage > 100 THEN
        RAISE EXCEPTION 'Total jar percentage would exceed 100%% (current: %%, adding: %%)', total, NEW.percentage;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER enforce_jar_percentage
    BEFORE INSERT OR UPDATE ON budget_jars
    FOR EACH ROW EXECUTE FUNCTION check_jar_percentage_sum();

CREATE INDEX idx_budget_jars_user ON budget_jars(user_id);

-- Many-to-many: jar <-> categories
CREATE TABLE budget_jar_categories (
    jar_id      BIGINT NOT NULL REFERENCES budget_jars(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (jar_id, category_id)
);
