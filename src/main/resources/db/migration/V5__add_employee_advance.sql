-- ============================================================================
-- V5 — Add advance column to employees table
-- ============================================================================

ALTER TABLE employees ADD COLUMN IF NOT EXISTS advance INT DEFAULT 0;
