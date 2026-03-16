-- V2__fix_ip_address_column.sql
-- Fix: Change ip_address from INET to VARCHAR

ALTER TABLE contact_submissions ALTER COLUMN ip_address TYPE VARCHAR(45);
