-- ============================================================================
-- Ritham ERP — Standalone Sample Data Seed Script (PostgreSQL 17)
-- Creates 10 customers, each with 1 to 5 orders (30 orders total), along with
-- canonical measurement profiles, order snapshots, payment ledger rows,
-- branches (if empty), and multi-day order history for analytics charts.
--
-- Idempotent by customer_mobile (`9840100001` .. `9840100010`): re-running will
-- skip any customer mobile that already exists with orders.
-- Run with: psql -h localhost -U ritham -d ritham_erp -v ON_ERROR_STOP=1 -f scripts/seed/seed-sample-data.sql
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_branch1_id BIGINT;
    v_branch2_id BIGINT;
    v_emp_id     BIGINT;
    v_emp_name   VARCHAR(100);
    v_order_id   BIGINT;
    v_seq        BIGINT;
    v_blouse_json TEXT;
    v_chudi_json  TEXT;
BEGIN
    -- ── 1. Ensure Primary Branch Exists ──────────────────────────────────────
    INSERT INTO branches (branch_code, name, city, address, phone, email, gst_number, is_active, created_at, updated_at)
    SELECT 'MN', 'Ritham Main Studio', 'Coimbatore', '42 Race Course Road, Gopalapuram', '0422-2221100', 'main@rithamdesigns.com', '33AABCR1234F1Z5', TRUE, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM branches WHERE branch_code = 'MN');

    SELECT id INTO v_branch1_id FROM branches ORDER BY id ASC LIMIT 1;
    SELECT id INTO v_branch2_id FROM branches ORDER BY id ASC OFFSET 1 LIMIT 1;
    IF v_branch2_id IS NULL THEN
        v_branch2_id := v_branch1_id;
    END IF;

    -- Resolve production employee for floor assignments
    SELECT id, full_name INTO v_emp_id, v_emp_name
    FROM employees
    WHERE is_active = TRUE
    ORDER BY CASE WHEN username = 'production' THEN 0 ELSE 1 END, id ASC
    LIMIT 1;

    -- Helper function inline via variables: reset sequence past any pre-existing orders
    PERFORM setval('order_number_seq', GREATEST(
        (SELECT last_value FROM order_number_seq),
        COALESCE((SELECT COUNT(*) FROM customer_orders), 0)
    ));

    -- ── Customer 1: Ananya Krishnan (9840100001) — 5 orders ──────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100001' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"14.5","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"34.0","B-2":"36","B-3":"30","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Pure Cotton","AV.":"Heavy aari work","SARI":"Falls & Pico"}';
        v_chudi_json  := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"34.0","L":"44","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"36","H":"38","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"Full Crepe","SHALL":"Matching","B.D.":"Ankle Pant"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100001', 'Ananya Krishnan', 5, NOW() - INTERVAL '19 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 5;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES
            ('9840100001', 'Ananya Krishnan', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'Boat neck with gold kasavu piping', NOW() - INTERVAL '19 days', NOW() - INTERVAL '2 days'),
            ('9840100001', 'Ananya Krishnan', 'CHUDI',  'WITH_LINING', v_chudi_json,  'Umbrella flare top with ankle pant', NOW() - INTERVAL '15 days', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '19 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100001', NOW() - INTERVAL '19 days', NOW() - INTERVAL '12 days', 'DELIVERED', 'PAID', 'UPI', 2400.00, 1000.00, 2400.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', 'Ananya Krishnan', v_branch1_id, NOW() - INTERVAL '19 days', NOW() - INTERVAL '12 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '19 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 1000.00, NOW() - INTERVAL '19 days'),
            (v_order_id, 'FINAL',   'UPI', 1400.00, NOW() - INTERVAL '12 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '14 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100001', NOW() - INTERVAL '14 days', NOW() - INTERVAL '7 days', 'COMPLETED', 'PAID', 'CASH', 1850.00, 1000.00, 1700.00, 150.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', 'Ananya Krishnan', v_branch1_id, NOW() - INTERVAL '14 days', NOW() - INTERVAL '7 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '14 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 1000.00, NOW() - INTERVAL '14 days'),
            (v_order_id, 'FINAL',   'CASH',  700.00, NOW() - INTERVAL '7 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '4 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100001', NOW() - INTERVAL '4 days', NOW() + INTERVAL '2 days', 'STITCHING', 'PARTIAL', 'UPI', 1600.00, 600.00, 1000.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '4 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '4 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI',  600.00, NOW() - INTERVAL '4 days'),
            (v_order_id, 'PARTIAL', 'CARD', 400.00, NOW() - INTERVAL '2 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '2 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100001', NOW() - INTERVAL '2 days', NOW() + INTERVAL '4 days', 'CUTTING', 'PARTIAL', 'CASH', 850.00, 350.00, 350.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '2 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 350.00, NOW() - INTERVAL '2 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100001', NOW(), NOW() + INTERVAL '6 days', 'PENDING', 'PENDING', 'CASH', 1100.00, 0.00, 0.00, 0.00, NULL, NULL, 'CHUDI', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW(), NOW())
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW());
    END IF;

    -- ── Customer 2: Priya Subramanian (9840100002) — 4 orders ────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100002' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"15.0","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"36.0","B-2":"38","B-3":"32","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Cotton Satin","AV.":"Zardosi motif","SARI":"Falls & Pico"}';
        v_chudi_json  := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"36.0","L":"41","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"38","H":"40","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"Cotton","SHALL":"Matching","B.D.":"Straight Pant"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100002', 'Priya Subramanian', 4, NOW() - INTERVAL '18 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 4;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES
            ('9840100002', 'Priya Subramanian', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'Deep pot back neck with tassels', NOW() - INTERVAL '18 days', NOW() - INTERVAL '3 days'),
            ('9840100002', 'Priya Subramanian', 'CHUDI',  'WITH_LINING', v_chudi_json,  'Straight kurta with pocket',      NOW() - INTERVAL '10 days', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '18 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100002', NOW() - INTERVAL '18 days', NOW() - INTERVAL '9 days', 'DELIVERED', 'PAID', 'CARD', 3200.00, 1500.00, 3000.00, 200.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', 'Priya Subramanian', v_branch2_id, NOW() - INTERVAL '18 days', NOW() - INTERVAL '9 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '18 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CARD', 1500.00, NOW() - INTERVAL '18 days'),
            (v_order_id, 'FINAL',   'CARD', 1500.00, NOW() - INTERVAL '9 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '8 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100002', NOW() - INTERVAL '8 days', NOW() - INTERVAL '1 day', 'QUALITY_CHECK', 'PARTIAL', 'UPI', 1500.00, 500.00, 500.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '8 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '8 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 500.00, NOW() - INTERVAL '8 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '5 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100002', NOW() - INTERVAL '5 days', NOW(), 'READY_TO_DELIVERY', 'PARTIAL', 'CASH', 1900.00, 900.00, 900.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '5 days', NOW())
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '5 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 900.00, NOW() - INTERVAL '5 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '1 day', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100002', NOW() - INTERVAL '1 day', NOW() + INTERVAL '5 days', 'DESIGNING', 'PENDING', 'UPI', 950.00, 0.00, 0.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITHOUT_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '1 day');
    END IF;

    -- ── Customer 3: Lakshmi Narayanan (9840100003) — 3 orders ────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100003' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"14.0","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"32.5","B-2":"34","B-3":"28","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Heavy Mulmul","AV.":"Mirror work cuffs","SARI":"Falls & Pico"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100003', 'Lakshmi Narayanan', 3, NOW() - INTERVAL '16 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 3;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100003', 'Lakshmi Narayanan', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'Princess cut padded blouse', NOW() - INTERVAL '16 days', NOW() - INTERVAL '2 days')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '16 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100003', NOW() - INTERVAL '16 days', NOW() - INTERVAL '8 days', 'DELIVERED', 'PAID', 'CASH', 2100.00, 1000.00, 2000.00, 100.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', 'Lakshmi Narayanan', v_branch1_id, NOW() - INTERVAL '16 days', NOW() - INTERVAL '8 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '16 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 1000.00, NOW() - INTERVAL '16 days'),
            (v_order_id, 'FINAL',   'UPI',  1000.00, NOW() - INTERVAL '8 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '6 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100003', NOW() - INTERVAL '6 days', NOW() + INTERVAL '1 day', 'READY_TO_DELIVERY', 'PARTIAL', 'BANK_TRANSFER', 1750.00, 750.00, 750.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '6 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '6 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'BANK_TRANSFER', 750.00, NOW() - INTERVAL '6 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '1 day', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100003', NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', 'DESIGNING', 'PENDING', 'CASH', 900.00, 0.00, 0.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '1 day');
    END IF;

    -- ── Customer 4: Meenakshi Sundaram (9840100004) — 2 orders ───────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100004' AND total_orders > 0) THEN
        v_chudi_json := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"38.0","L":"43","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"40","H":"42","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"Pure Cotton","SHALL":"Matching","B.D.":"Palazzo"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100004', 'Meenakshi Sundaram', 2, NOW() - INTERVAL '9 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 2;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100004', 'Meenakshi Sundaram', 'CHUDI', 'WITH_LINING', v_chudi_json, 'Collar neck kurti with side pockets', NOW() - INTERVAL '9 days', NOW() - INTERVAL '2 days')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '7 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100004', NOW() - INTERVAL '7 days', NOW(), 'QUALITY_CHECK', 'PARTIAL', 'UPI', 1650.00, 650.00, 650.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '7 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '7 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 650.00, NOW() - INTERVAL '7 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '3 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100004', NOW() - INTERVAL '3 days', NOW() + INTERVAL '2 days', 'FINAL_IRONING', 'PAID', 'CARD', 1400.00, 1400.00, 1400.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '3 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CARD', 1400.00, NOW() - INTERVAL '3 days');
    END IF;

    -- ── Customer 5: Kavitha Rajendran (9840100005) — 1 order ─────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100005' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"13.5","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"33.0","B-2":"35","B-3":"29","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"None","AV.":"Plain piping","SARI":"Falls & Pico"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100005', 'Kavitha Rajendran', 1, NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 1;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100005', 'Kavitha Rajendran', 'BLOUSE', 'WITHOUT_LINING', v_blouse_json, 'Daily wear katori cut cotton blouse', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100005', NOW(), NOW() + INTERVAL '4 days', 'PENDING', 'PENDING', 'CASH', 750.00, 0.00, 0.00, 0.00, NULL, NULL, 'BLOUSE', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW(), NOW())
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW());
    END IF;

    -- ── Customer 6: Divya Bharathi (9840100006) — 5 orders ────────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100006' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"15.0","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"35.0","B-2":"37","B-3":"31","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Shantoon Silk","AV.":"Bridal maggam gold beadwork","SARI":"Falls & Pico"}';
        v_chudi_json  := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"35.0","L":"48","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"37","H":"39","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"Santoon Full","SHALL":"Matching","B.D.":"Chudi"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100006', 'Divya Bharathi', 5, NOW() - INTERVAL '20 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 5;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES
            ('9840100006', 'Divya Bharathi', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'Bridal silk blouse with elbow sleeves', NOW() - INTERVAL '20 days', NOW() - INTERVAL '2 days'),
            ('9840100006', 'Divya Bharathi', 'CHUDI',  'WITH_LINING', v_chudi_json,  'Anarkali reception suit',             NOW() - INTERVAL '12 days', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '20 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100006', NOW() - INTERVAL '20 days', NOW() - INTERVAL '10 days', 'COMPLETED', 'PAID', 'BANK_TRANSFER', 4800.00, 2000.00, 4500.00, 300.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', 'Divya Bharathi', v_branch1_id, NOW() - INTERVAL '20 days', NOW() - INTERVAL '10 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '20 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'BANK_TRANSFER', 2000.00, NOW() - INTERVAL '20 days'),
            (v_order_id, 'FINAL',   'UPI',           2500.00, NOW() - INTERVAL '10 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '9 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100006', NOW() - INTERVAL '9 days', NOW() + INTERVAL '1 day', 'READY_TO_DELIVERY', 'PARTIAL', 'CARD', 2600.00, 1200.00, 1200.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '9 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '9 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CARD', 1200.00, NOW() - INTERVAL '9 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '5 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100006', NOW() - INTERVAL '5 days', NOW() + INTERVAL '3 days', 'HEMMING', 'PARTIAL', 'UPI', 2200.00, 1000.00, 1500.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '5 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI',  1000.00, NOW() - INTERVAL '5 days'),
            (v_order_id, 'PARTIAL', 'CASH',  500.00, NOW() - INTERVAL '2 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '2 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100006', NOW() - INTERVAL '2 days', NOW() + INTERVAL '6 days', 'LINING', 'PENDING', 'CASH', 1750.00, 0.00, 0.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '2 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '6 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100006', NOW() - INTERVAL '6 days', NOW() + INTERVAL '8 days', 'CANCELLED', 'PENDING', 'CASH', 900.00, 0.00, 0.00, 0.00, NULL, NULL, 'BLOUSE', 'WITHOUT_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '6 days');
    END IF;

    -- ── Customer 7: Sangeetha Venkat (9840100007) — 3 orders ──────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100007' AND total_orders > 0) THEN
        v_chudi_json := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"32.0","L":"40","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"34","H":"36","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"Voile Cotton","SHALL":"Matching","B.D.":"Cigarette Pant"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100007', 'Sangeetha Venkat', 3, NOW() - INTERVAL '13 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 3;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100007', 'Sangeetha Venkat', 'CHUDI', 'WITH_LINING', v_chudi_json, 'V-neck ethnic kurta with gathered sleeves', NOW() - INTERVAL '13 days', NOW() - INTERVAL '2 days')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '13 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100007', NOW() - INTERVAL '13 days', NOW() - INTERVAL '6 days', 'DELIVERED', 'PAID', 'CARD', 1550.00, 750.00, 1500.00, 50.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', 'Karthik Venkat', v_branch2_id, NOW() - INTERVAL '13 days', NOW() - INTERVAL '6 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '13 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CARD', 750.00, NOW() - INTERVAL '13 days'),
            (v_order_id, 'FINAL',   'CARD', 750.00, NOW() - INTERVAL '6 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '4 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100007', NOW() - INTERVAL '4 days', NOW() + INTERVAL '3 days', 'HAND_MACHINE_WORK', 'PARTIAL', 'UPI', 2100.00, 800.00, 800.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '4 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '4 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 800.00, NOW() - INTERVAL '4 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '2 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100007', NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', 'STITCHING', 'PARTIAL', 'CASH', 1150.00, 400.00, 400.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '2 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 400.00, NOW() - INTERVAL '2 days');
    END IF;

    -- ── Customer 8: Revathi Balasubramani (9840100008) — 4 orders ────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100008' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"15.5","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"37.0","B-2":"39","B-3":"33","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Pure Silk Cotton","AV.":"Pearl edging","SARI":"Falls & Pico"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100008', 'Revathi Balasubramani', 4, NOW() - INTERVAL '15 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 4;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100008', 'Revathi Balasubramani', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'High neck saree blouse with zip', NOW() - INTERVAL '15 days', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '15 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100008', NOW() - INTERVAL '15 days', NOW() - INTERVAL '5 days', 'DELIVERED', 'PAID', 'BANK_TRANSFER', 2800.00, 1400.00, 2800.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', 'Revathi Balasubramani', v_branch1_id, NOW() - INTERVAL '15 days', NOW() - INTERVAL '5 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '15 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'BANK_TRANSFER', 1400.00, NOW() - INTERVAL '15 days'),
            (v_order_id, 'FINAL',   'BANK_TRANSFER', 1400.00, NOW() - INTERVAL '5 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '6 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100008', NOW() - INTERVAL '6 days', NOW(), 'READY_TO_DELIVERY', 'PAID', 'UPI', 1950.00, 1950.00, 1950.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '6 days', NOW())
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '6 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 1950.00, NOW() - INTERVAL '6 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '3 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100008', NOW() - INTERVAL '3 days', NOW() + INTERVAL '2 days', 'STRETCHING', 'PARTIAL', 'CASH', 1500.00, 500.00, 500.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '3 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 500.00, NOW() - INTERVAL '3 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '1 day', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100008', NOW() - INTERVAL '1 day', NOW() + INTERVAL '4 days', 'INITIAL_IRONING', 'PARTIAL', 'CARD', 1700.00, 700.00, 700.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '1 day');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CARD', 700.00, NOW() - INTERVAL '1 day');
    END IF;

    -- ── Customer 9: Nandhini Murugan (9840100009) — 2 orders ─────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100009' AND total_orders > 0) THEN
        v_chudi_json := '{"F.N.":"6.0","B.N.":"7.0","H.B.":"33.0","L":"39","S.S.":"18.0","S.L.":"15.0","S.LO":"10.5","AM":"13.0","B":"35","H":"37","T.S.":"21.0","P.L.":"38.0","S.":"24.0","L.":"12.0","S.CUT":"Straight","LNG":"No Lining","SHALL":"Matching","B.D.":"Patiala"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100009', 'Nandhini Murugan', 2, NOW() - INTERVAL '11 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 2;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100009', 'Nandhini Murugan', 'CHUDI', 'WITHOUT_LINING', v_chudi_json, 'Casual cotton chudi set', NOW() - INTERVAL '11 days', NOW() - INTERVAL '2 days')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '11 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100009', NOW() - INTERVAL '11 days', NOW() - INTERVAL '4 days', 'COMPLETED', 'PAID', 'CASH', 450.00, 200.00, 450.00, 0.00, v_emp_id, v_emp_name, NULL, NULL, 'ERP', 'Nandhini Murugan', v_branch1_id, NOW() - INTERVAL '11 days', NOW() - INTERVAL '4 days')
        RETURNING id INTO v_order_id;
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'CASH', 200.00, NOW() - INTERVAL '11 days'),
            (v_order_id, 'FINAL',   'CASH', 250.00, NOW() - INTERVAL '4 days');

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '2 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100009', NOW() - INTERVAL '2 days', NOW() + INTERVAL '3 days', 'CUTTING', 'PARTIAL', 'UPI', 1200.00, 500.00, 500.00, 0.00, v_emp_id, v_emp_name, 'CHUDI', 'WITHOUT_LINING', 'ERP', NULL, v_branch1_id, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'CHUDI', v_chudi_json, NOW() - INTERVAL '2 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 500.00, NOW() - INTERVAL '2 days');
    END IF;

    -- ── Customer 10: Deepika Senthil (9840100010) — 1 order ──────────────────
    IF NOT EXISTS (SELECT 1 FROM customers WHERE customer_mobile = '9840100010' AND total_orders > 0) THEN
        v_blouse_json := '{"LTH":"14.2","SHO":"14.5","H.S.":"6.5","H.L.":"10.0","H.LO":"11.0","AK":"15.5","AM":"12.0","BN":"9.0","FN":"6.5","DP-1":"9.5","DP-2":"11.0","B-1":"31.0","B-2":"33","B-3":"27.5","F.HOOK":"Yes","B.HOOK":"Knot","LINING":"Cotton","AV.":"Contrast thread piping","SARI":"Falls & Pico"}';

        INSERT INTO customers (customer_mobile, customer_name, total_orders, created_at)
        VALUES ('9840100010', 'Deepika Senthil', 1, NOW() - INTERVAL '2 days')
        ON CONFLICT (customer_mobile) DO UPDATE SET customer_name = EXCLUDED.customer_name, total_orders = 1;

        INSERT INTO customer_measurements (customer_mobile, customer_name, garment_type, lining, measurements_json, notes, created_at, updated_at)
        VALUES ('9840100010', 'Deepika Senthil', 'BLOUSE', 'WITH_LINING', v_blouse_json, 'Sweetheart neck silk cotton blouse', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        ON CONFLICT (customer_mobile, garment_type) DO NOTHING;

        v_seq := nextval('order_number_seq');
        INSERT INTO customer_orders (order_number, customer_mobile, order_date, delivery_date, status, payment_status, payment_mode, total_amount, advance_amount, paid_amount, discount_amount, assigned_employee_id, assigned_employee_name, garment_type, lining, source, receiver_name, branch_id, created_at, updated_at)
        VALUES ('ORD-' || TO_CHAR(NOW() - INTERVAL '2 days', 'YYYYMMDD') || '-' || LPAD(v_seq::TEXT, 4, '0'), '9840100010', NOW() - INTERVAL '2 days', NOW() + INTERVAL '2 days', 'STITCHING', 'PARTIAL', 'UPI', 1650.00, 650.00, 650.00, 0.00, v_emp_id, v_emp_name, 'BLOUSE', 'WITH_LINING', 'ERP', NULL, v_branch2_id, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
        RETURNING id INTO v_order_id;
        INSERT INTO order_measurements (order_id, garment_type, measurements_json, created_at) VALUES (v_order_id, 'BLOUSE', v_blouse_json, NOW() - INTERVAL '2 days');
        INSERT INTO order_payments (order_id, payment_type, payment_method, amount, payment_date) VALUES
            (v_order_id, 'ADVANCE', 'UPI', 650.00, NOW() - INTERVAL '2 days');
    END IF;
END $$;

COMMIT;

SELECT 'branches' AS tbl, COUNT(*) AS cnt FROM branches
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'customer_measurements', COUNT(*) FROM customer_measurements
UNION ALL SELECT 'customer_orders', COUNT(*) FROM customer_orders
UNION ALL SELECT 'order_measurements', COUNT(*) FROM order_measurements
UNION ALL SELECT 'order_payments', COUNT(*) FROM order_payments;
