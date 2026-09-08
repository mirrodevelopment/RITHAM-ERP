-- ============================================================================
-- V9 — Create Branches and Multi-Tenancy Architecture
-- Supports multiple company branches with isolated orders, employees, and operations.
-- System Administrator (admin) is global (branch_id = NULL) across all branches.
-- ============================================================================

-- 1. Create Branches table
CREATE TABLE IF NOT EXISTS branches (
    id           BIGSERIAL    PRIMARY KEY,
    branch_code  VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    city         VARCHAR(50),
    address      VARCHAR(255),
    phone        VARCHAR(20),
    email        VARCHAR(100),
    gst_number   VARCHAR(30),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. Seed Initial Branches (using WHERE NOT EXISTS for safe idempotency)
INSERT INTO branches (branch_code, name, city, address, phone, email, gst_number, is_active)
SELECT 'BR-001', 'Ritham Designs — Main Branch', 'Chennai', '12/4 Anna Salai, T. Nagar, Chennai - 600017', '9000000001', 'main.branch@rithamdesigns.com', '33AAAAA0000A1Z5', TRUE
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_code = 'BR-001');

INSERT INTO branches (branch_code, name, city, address, phone, email, gst_number, is_active)
SELECT 'BR-002', 'Ritham Designs — City Branch', 'Coimbatore', '45 Cross Cut Road, RS Puram, Coimbatore - 641002', '9000000002', 'cbe.branch@rithamdesigns.com', '33AAAAA0000A1Z6', TRUE
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_code = 'BR-002');

-- 3. Add branch_id to employees table
ALTER TABLE employees ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);
CREATE INDEX IF NOT EXISTS idx_employees_branch ON employees(branch_id);

-- Assign all existing employees to Branch 1 (Main Branch), except admin (admin has NULL = Super Admin)
UPDATE employees SET branch_id = 1 WHERE username IS NULL OR username != 'admin';
UPDATE employees SET branch_id = NULL WHERE username = 'admin';

-- 4. Add branch_id to customer_orders table
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);
CREATE INDEX IF NOT EXISTS idx_orders_branch ON customer_orders(branch_id);

-- Assign all existing orders to Branch 1 (Main Branch)
UPDATE customer_orders SET branch_id = 1 WHERE branch_id IS NULL;

-- 5. Seed Branch 2 (Coimbatore) system staff accounts
-- Role 2: Operations Manager, Role 4: Reception, Role 3: Production Floor
INSERT INTO employees (employee_code, full_name, mobile_number, email, username, password_hash, role_id, branch_id, is_active)
SELECT 'EMP-201', 'Coimbatore Branch Manager', '9000000021', 'manager.cbe@rithamdesigns.com', 'manager_cbe', '$2a$12$4v0wXwU/76T05mU9YI4Xh.3v3GjGkZ7Q7JzI0N4iJ3Zz3Q7JzI0N4', 2, 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_code = 'EMP-201');

INSERT INTO employees (employee_code, full_name, mobile_number, email, username, password_hash, role_id, branch_id, is_active)
SELECT 'EMP-202', 'Coimbatore Reception Desk', '9000000022', 'reception.cbe@rithamdesigns.com', 'reception_cbe', '$2a$12$4v0wXwU/76T05mU9YI4Xh.3v3GjGkZ7Q7JzI0N4iJ3Zz3Q7JzI0N4', 4, 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_code = 'EMP-202');

INSERT INTO employees (employee_code, full_name, mobile_number, email, username, password_hash, role_id, branch_id, is_active)
SELECT 'EMP-203', 'Coimbatore Production Lead', '9000000023', 'production.cbe@rithamdesigns.com', 'production_cbe', '$2a$12$4v0wXwU/76T05mU9YI4Xh.3v3GjGkZ7Q7JzI0N4iJ3Zz3Q7JzI0N4', 3, 2, TRUE
WHERE NOT EXISTS (SELECT 1 FROM employees WHERE employee_code = 'EMP-203');
