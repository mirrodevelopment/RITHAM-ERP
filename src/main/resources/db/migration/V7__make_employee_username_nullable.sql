-- ============================================================================
-- V7 — Make username nullable on employees table
-- System Users (admin, manager, reception, production) have usernames.
-- Regular Employees do not have or require usernames.
-- ============================================================================

ALTER TABLE employees ALTER COLUMN username DROP NOT NULL;
