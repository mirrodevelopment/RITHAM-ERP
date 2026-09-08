-- ============================================================================
-- V15 — Drop Historical Data Migration Staging Tables
-- ============================================================================
-- Drops the isolated staging tables created for the historical migration desk:
--   1. migration_imports
--   2. migration_reviews
--   3. migration_documents
--   4. migration_batches
-- ============================================================================

DROP TABLE IF EXISTS migration_imports CASCADE;
DROP TABLE IF EXISTS migration_reviews CASCADE;
DROP TABLE IF EXISTS migration_documents CASCADE;
DROP TABLE IF EXISTS migration_batches CASCADE;
