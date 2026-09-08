-- ============================================================================
-- V6 — Add joining_date column to employees table
-- ============================================================================

ALTER TABLE employees ADD COLUMN IF NOT EXISTS joining_date DATE;
