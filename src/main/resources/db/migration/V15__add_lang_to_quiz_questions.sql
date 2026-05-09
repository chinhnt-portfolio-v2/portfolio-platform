-- Adds lang column to support bilingual question bank (English + Vietnamese)
ALTER TABLE quiz_questions ADD COLUMN lang VARCHAR(2) NOT NULL DEFAULT 'en';
