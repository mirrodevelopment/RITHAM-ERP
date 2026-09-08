-- ============================================================================
-- V11 — Add Optimistic Locking Version to Customer Orders
-- ============================================================================

-- Add version column to customer_orders table for optimistic locking (concurrency control)
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;
