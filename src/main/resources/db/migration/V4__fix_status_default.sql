-- ============================================================================
-- V4 — Fix order status default alignment
-- BUG-14: V1 schema sets DEFAULT 'DESIGNING' but Java entity defaults to 'PENDING'.
-- This migration aligns the DB default to match Java so fresh inserts are consistent.
-- ============================================================================

-- Align customer_orders default status to PENDING (matches Java @Builder.Default)
ALTER TABLE customer_orders ALTER COLUMN status SET DEFAULT 'PENDING';
