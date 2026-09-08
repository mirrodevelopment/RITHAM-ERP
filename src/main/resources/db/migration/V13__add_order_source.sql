-- ============================================================================
-- V13 — Add source column to customer_orders for historical migration tracking
-- ============================================================================
-- Allows distinguishing historical imported orders from normal ERP orders.
-- Default is 'ERP' (all existing orders). Migration imports set 'HISTORICAL_MIGRATION'.
-- ============================================================================

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'ERP';

-- Optional: index to filter historical vs live orders in reports
CREATE INDEX IF NOT EXISTS idx_customer_orders_source ON customer_orders(source);
