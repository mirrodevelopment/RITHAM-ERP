-- ============================================================================
-- V10 — Add Order Number Sequence and Uniqueness Protection
-- ============================================================================

-- 1. Create order number sequence starting at 1001
CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1001 INCREMENT BY 1;

-- 2. Ensure unique index on order_number exists for explicit uniqueness protection
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_order_number_unique ON customer_orders(order_number);
