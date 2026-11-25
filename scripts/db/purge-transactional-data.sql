-- ============================================================================
-- Ritham ERP — Purge Transactional Data (PostgreSQL)
-- Preserves: empty schema tables, Flyway metadata, branches, roles,
--            departments, employees (user accounts), refresh_tokens,
--            and production_stages.
-- Removes:   all customers, customer_measurements, customer_orders,
--            order_* sub-tables, production_tracking, and migration_* rows.
-- Resets:    emptied identity sequences and order_number_seq back to 1.
-- ============================================================================

BEGIN;

TRUNCATE TABLE
    migration_imports,
    migration_reviews,
    migration_documents,
    migration_batches,
    order_deliveries,
    order_payments,
    production_tracking,
    order_materials,
    order_measurements,
    order_designs,
    customer_orders,
    customer_measurements,
    customers
RESTART IDENTITY;

ALTER SEQUENCE order_number_seq RESTART WITH 1;

COMMIT;
