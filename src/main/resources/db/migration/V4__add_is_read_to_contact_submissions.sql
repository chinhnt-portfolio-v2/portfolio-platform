-- V4__add_is_read_to_contact_submissions.sql
-- Story: 6-3 — Admin Contact Inquiries Endpoint (FR37)
-- Adds is_read flag so owner can mark submissions as viewed.

ALTER TABLE contact_submissions
    ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for filtering unread submissions
CREATE INDEX idx_contact_is_read ON contact_submissions(is_read);
