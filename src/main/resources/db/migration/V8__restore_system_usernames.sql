-- ============================================================================
-- V8 — Restore nullable username column on employees table
-- Allows System Users (admin, manager, reception, production) to have usernames,
-- while regular Employees remain without usernames (NULL).
-- ============================================================================

ALTER TABLE employees ADD COLUMN IF NOT EXISTS username VARCHAR(50);

-- Restore usernames for the 4 dashboard system users
UPDATE employees SET username = 'admin' WHERE employee_code = 'EMP-001' OR mobile_number = '9000000001';
UPDATE employees SET username = 'manager' WHERE employee_code = 'EMP-002' OR mobile_number = '9000000002';
UPDATE employees SET username = 'reception' WHERE employee_code = 'EMP-003' OR mobile_number = '9000000003';
UPDATE employees SET username = 'production' WHERE employee_code = 'EMP-004' OR mobile_number = '9000000004';
