-- ============================================================================
-- V2 — Ritham ERP Minimal Seed Baseline Data
-- Contains only required system metadata, branches, roles, stages & login accounts.
-- 0 fake customers, 0 fake orders, 0 fake measurements (Empty production baseline).
-- ============================================================================

-- ── 1. Roles ─────────────────────────────────────────────────────────────────
INSERT INTO roles (id, name, description, created_at) VALUES
    (1, 'ROLE_ADMIN',               'Administrator — full system access', NOW()),
    (2, 'ROLE_OPERATIONS_MANAGER',  'Operations Manager — monitors orders, payments and production', NOW()),
    (3, 'ROLE_PRODUCTION_EMPLOYEE', 'Production floor — works on assigned garment stages', NOW()),
    (4, 'ROLE_RECEPTION',           'Reception — registers customers and creates orders', NOW());

-- ── 2. Departments ───────────────────────────────────────────────────────────
INSERT INTO departments (id, name, created_at) VALUES
    (1, 'Administration', NOW()),
    (2, 'Production',     NOW()),
    (3, 'Operations',     NOW()),
    (4, 'Reception',      NOW());

-- ── 3. Production Stages (12 Tailoring Stages Workflow) ─────────────────────
INSERT INTO production_stages (stage_key, title, description, icon, display_order, color, bg_color, is_active, created_at, updated_at) VALUES
    ('DESIGNING',           'Designing',                'Design consultation, styling & pattern drafting.',       'D', 1,  '#818CF8', 'rgba(99, 102, 241, 0.15)', TRUE, NOW(), NOW()),
    ('LINING',              'Lining',                   'Lining fabric selection, pre-wash & matching.',           'L', 2,  '#A78BFA', 'rgba(167, 139, 250, 0.15)', TRUE, NOW(), NOW()),
    ('HAND_MACHINE_WORK',   'Hand Work / Machine Work', 'Aari, zardosi, embroidery & machine stitching.',          'H', 3,  '#EC4899', 'rgba(236, 72, 153, 0.15)', TRUE, NOW(), NOW()),
    ('INITIAL_IRONING',     'Initial Ironing',          'Pre-cutting fabric steam pressing & smoothing.',          'I', 4,  '#F59E0B', 'rgba(245, 158, 11, 0.15)', TRUE, NOW(), NOW()),
    ('CUTTING',             'Cutting',                  'Master fabric cutting & darts sectioning.',                'C', 5,  '#EF4444', 'rgba(239, 68, 68, 0.15)', TRUE, NOW(), NOW()),
    ('STRETCHING',          'Stretching',               'Fabric tensioning & body contour fitting.',               'S', 6,  '#14B8A6', 'rgba(20, 184, 166, 0.15)', TRUE, NOW(), NOW()),
    ('STITCHING',           'Stitching',                'Main garment assembly & seams joining.',                  'S', 7,  '#3B82F6', 'rgba(59, 130, 246, 0.15)', TRUE, NOW(), NOW()),
    ('HEMMING',             'Hemming',                  'Edge hemming, piping, hooks & eye finish.',               'H', 8,  '#6366F1', 'rgba(99, 102, 241, 0.15)', TRUE, NOW(), NOW()),
    ('FINAL_IRONING',       'Final Ironing',            'Final steam press & silhouette alignment.',               'F', 9,  '#FB923C', 'rgba(251, 146, 60, 0.15)', TRUE, NOW(), NOW()),
    ('QUALITY_CHECK',       'Quality Check (QC)',       'Measurement verification & quality audit.',               'Q', 10, '#10B981', 'rgba(16, 185, 129, 0.15)', TRUE, NOW(), NOW()),
    ('READY_TO_DELIVERY',   'Ready to Delivery',        'Garment tagging, hanger packing & pickup ready.',         'R', 11, '#06B6D4', 'rgba(6, 182, 212, 0.15)', TRUE, NOW(), NOW()),
    ('DELIVERY',            'Delivery',                 'Order dispatched & handed over to customer.',             'D', 12, '#22C55E', 'rgba(34, 197, 94, 0.15)', TRUE, NOW(), NOW());

-- ── 4. System Login Accounts & Staff ─────────────────────────────────────────
-- Passwords will be encoded natively on first server startup via DataInitializer.
-- Branches are not pre-seeded; add real branches via the Branch Desk.
INSERT INTO employees (employee_code, full_name, mobile_number, email, username, password_hash, role_id, department_id, branch_id, stage, advance, is_active, created_at, updated_at) VALUES
    ('EMP-001', 'System Administrator',  '9000000001', 'admin@rithamdesigns.com',       'admin',          'ENCODE_ON_STARTUP', 1, 1, NULL, NULL, 0, TRUE, NOW(), NOW()),
    ('EMP-002', 'Operations Manager',    '9000000002', 'manager@rithamdesigns.com',     'manager',        'ENCODE_ON_STARTUP', 2, 3, NULL, NULL, 0, TRUE, NOW(), NOW()),
    ('EMP-003', 'Front Desk Reception',  '9000000003', 'reception@rithamdesigns.com',   'reception',      'ENCODE_ON_STARTUP', 4, 4, NULL, NULL, 0, TRUE, NOW(), NOW()),
    ('EMP-004', 'Production Supervisor', '9000000004', 'production@rithamdesigns.com',  'production',     'ENCODE_ON_STARTUP', 3, 2, NULL, NULL, 0, TRUE, NOW(), NOW());
    