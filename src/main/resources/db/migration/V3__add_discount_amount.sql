-- Migration: V3__add_discount_amount.sql
-- Description: Add discount_amount to customer_orders table for delivery discounts

ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2) DEFAULT 0;
