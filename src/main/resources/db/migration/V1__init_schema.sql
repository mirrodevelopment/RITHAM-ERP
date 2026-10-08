-- ============================================================================
-- V1 — Ritham ERP Unified Baseline Schema
-- Clean unified schema: multi-tenancy, RBAC, orders, production & migration
-- ============================================================================

-- ── 1. Company Branches & Multi-Tenancy ──────────────────────────────────────
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

-- ── 2. Security & RBAC ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS departments (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS employees (
    id              BIGSERIAL    PRIMARY KEY,
    employee_code   VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    mobile_number   VARCHAR(15)  NOT NULL UNIQUE,
    email           VARCHAR(100),
    username        VARCHAR(50)  UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         BIGINT       NOT NULL REFERENCES roles(id),
    department_id   BIGINT       REFERENCES departments(id),
    branch_id       BIGINT       REFERENCES branches(id),
    stage           VARCHAR(50),
    advance         INT          NOT NULL DEFAULT 0,
    joining_date    DATE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employees_username ON employees(username);
CREATE INDEX IF NOT EXISTS idx_employees_mobile   ON employees(mobile_number);
CREATE INDEX IF NOT EXISTS idx_employees_role     ON employees(role_id);
CREATE INDEX IF NOT EXISTS idx_employees_branch   ON employees(branch_id);
CREATE INDEX IF NOT EXISTS idx_employees_stage    ON employees(stage);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT    NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    token        TEXT      NOT NULL UNIQUE,
    expires_at   TIMESTAMP NOT NULL,
    is_revoked   BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_employee ON refresh_tokens(employee_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token    ON refresh_tokens(token);

-- ── 3. Customers ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS customers (
    customer_mobile VARCHAR(15)  PRIMARY KEY,
    customer_name   VARCHAR(100) NOT NULL,
    total_orders    INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(customer_name);

-- ── 4. Customer Measurements (Directly Linked to Mobile Number) ──────────────
CREATE TABLE IF NOT EXISTS customer_measurements (
    id                BIGSERIAL    PRIMARY KEY,
    customer_mobile   VARCHAR(15)  NOT NULL REFERENCES customers(customer_mobile) ON DELETE CASCADE,
    customer_name     VARCHAR(100) NOT NULL,
    garment_type      VARCHAR(30)  NOT NULL,
    lining            VARCHAR(30),
    measurements_json TEXT         NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_cust_msr_mobile_garment UNIQUE (customer_mobile, garment_type)
);

CREATE INDEX IF NOT EXISTS idx_cust_msr_mobile  ON customer_measurements(customer_mobile);
CREATE INDEX IF NOT EXISTS idx_cust_msr_garment ON customer_measurements(garment_type);

-- ── 5. Customer Orders & Sequence ────────────────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS customer_orders (
    id                     BIGSERIAL     PRIMARY KEY,
    order_number           VARCHAR(30)   NOT NULL UNIQUE,
    customer_mobile        VARCHAR(15)   NOT NULL REFERENCES customers(customer_mobile),
    order_date             TIMESTAMP     NOT NULL DEFAULT NOW(),
    delivery_date          TIMESTAMP     NOT NULL,
    status                 VARCHAR(50)   NOT NULL DEFAULT 'DESIGNING',
    payment_status         VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    payment_mode           VARCHAR(30)   DEFAULT 'CASH',
    total_amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    advance_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    paid_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    assigned_employee_id   BIGINT        REFERENCES employees(id),
    assigned_employee_name VARCHAR(100),
    garment_type           VARCHAR(50),
    lining                 VARCHAR(50),
    source                 VARCHAR(30)   NOT NULL DEFAULT 'ERP',
    receiver_name          VARCHAR(100),
    branch_id              BIGINT        REFERENCES branches(id),
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_mobile ON customer_orders(customer_mobile);
CREATE INDEX IF NOT EXISTS idx_orders_status          ON customer_orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_delivery_date   ON customer_orders(delivery_date);
CREATE INDEX IF NOT EXISTS idx_orders_employee        ON customer_orders(assigned_employee_id);
CREATE INDEX IF NOT EXISTS idx_orders_branch          ON customer_orders(branch_id);

-- ── 6. Order Sub-Entities ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_designs (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    design_name VARCHAR(150),
    notes       TEXT,
    image_url   TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_designs_order ON order_designs(order_id);

CREATE TABLE IF NOT EXISTS order_measurements (
    id                BIGSERIAL    PRIMARY KEY,
    order_id          BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    garment_type      VARCHAR(100) NOT NULL,
    measurements_json TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_measurements_order ON order_measurements(order_id);

CREATE TABLE IF NOT EXISTS order_materials (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    material_source VARCHAR(20)  NOT NULL DEFAULT 'COMPANY',
    material_name   VARCHAR(150) NOT NULL,
    quantity        NUMERIC(10,2),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_materials_order ON order_materials(order_id);

CREATE TABLE IF NOT EXISTS production_tracking (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    production_stage VARCHAR(50)  NOT NULL,
    employee_id      BIGINT       REFERENCES employees(id),
    assigned_date    TIMESTAMP,
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    remarks          TEXT,
    image_url        TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_production_order    ON production_tracking(order_id);
CREATE INDEX IF NOT EXISTS idx_production_employee ON production_tracking(employee_id);
CREATE INDEX IF NOT EXISTS idx_production_stage    ON production_tracking(production_stage);

CREATE TABLE IF NOT EXISTS order_payments (
    id             BIGSERIAL     PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    payment_type   VARCHAR(20)   NOT NULL,
    payment_method VARCHAR(30)   NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    payment_date   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payments_order ON order_payments(order_id);

CREATE TABLE IF NOT EXISTS order_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT    NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    delivery_date   TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_by    BIGINT    REFERENCES employees(id),
    is_delivered    BOOLEAN   NOT NULL DEFAULT FALSE,
    receipt_printed BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_deliveries_order ON order_deliveries(order_id);

-- ── 7. Production Stages Master (12 Tailoring Stages) ────────────────────────
CREATE TABLE IF NOT EXISTS production_stages (
    id            BIGSERIAL    PRIMARY KEY,
    stage_key     VARCHAR(50)  NOT NULL UNIQUE,
    title         VARCHAR(100) NOT NULL,
    description   VARCHAR(255),
    icon          VARCHAR(10)  DEFAULT '🧵',
    display_order INT          NOT NULL,
    color         VARCHAR(20)  DEFAULT '#818CF8',
    bg_color      VARCHAR(50)  DEFAULT 'rgba(99, 102, 241, 0.15)',
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stage_order  ON production_stages(display_order);
CREATE INDEX IF NOT EXISTS idx_stage_active ON production_stages(is_active);

-- ── 8. Historical Data Migration Staging Tables ─────────────────────────────
CREATE TABLE IF NOT EXISTS migration_batches (
    id               BIGSERIAL       PRIMARY KEY,
    batch_code       VARCHAR(30)     NOT NULL UNIQUE,
    branch_id        BIGINT          REFERENCES branches(id) ON DELETE RESTRICT,
    description      VARCHAR(255),
    source_year      SMALLINT        NOT NULL,
    total_documents  INTEGER         NOT NULL DEFAULT 0,
    uploaded_count   INTEGER         NOT NULL DEFAULT 0,
    processed_count  INTEGER         NOT NULL DEFAULT 0,
    reviewed_count   INTEGER         NOT NULL DEFAULT 0,
    approved_count   INTEGER         NOT NULL DEFAULT 0,
    imported_count   INTEGER         NOT NULL DEFAULT 0,
    failed_count     INTEGER         NOT NULL DEFAULT 0,
    status           VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    created_by       BIGINT          REFERENCES employees(id) ON DELETE SET NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_migration_batches_branch    ON migration_batches(branch_id);
CREATE INDEX IF NOT EXISTS idx_migration_batches_status    ON migration_batches(status);
CREATE INDEX IF NOT EXISTS idx_migration_batches_year      ON migration_batches(source_year);

CREATE TABLE IF NOT EXISTS migration_documents (
    id                       BIGSERIAL       PRIMARY KEY,
    batch_id                 BIGINT          NOT NULL REFERENCES migration_batches(id) ON DELETE CASCADE,
    document_code            VARCHAR(40)     NOT NULL UNIQUE,
    file_name                VARCHAR(255)    NOT NULL,
    file_path                VARCHAR(1000)   NOT NULL,
    file_type                VARCHAR(10)     NOT NULL,
    source_year              SMALLINT        NOT NULL,

    -- Multi-page PDF metadata
    page_number              INTEGER         NOT NULL DEFAULT 1,
    total_pages              INTEGER         NOT NULL DEFAULT 1,
    source_file_name         VARCHAR(255),
    source_file_path         VARCHAR(1000),

    -- OCR processing & version metadata
    ocr_processed_image_path VARCHAR(1000),
    ocr_version              VARCHAR(20)     DEFAULT '2.0',
    template_version         VARCHAR(20)     DEFAULT '2.0',
    preprocessing_version    VARCHAR(20)     DEFAULT '2.0',

    ocr_status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    extraction_status        VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    review_status            VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    import_status            VARCHAR(30)     NOT NULL DEFAULT 'PENDING',

    raw_ocr_text             TEXT,
    ocr_confidence           DECIMAL(5, 2),

    extracted_data_json      TEXT,
    corrected_data_json      TEXT,
    error_message            VARCHAR(1000),

    created_by               BIGINT          REFERENCES employees(id) ON DELETE SET NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_documents_batch         ON migration_documents(batch_id);
CREATE INDEX IF NOT EXISTS idx_migration_documents_ocr_status    ON migration_documents(ocr_status);
CREATE INDEX IF NOT EXISTS idx_migration_documents_review_status ON migration_documents(review_status);
CREATE INDEX IF NOT EXISTS idx_migration_documents_import_status ON migration_documents(import_status);
CREATE INDEX IF NOT EXISTS idx_migration_documents_source_file   ON migration_documents(source_file_name);
CREATE INDEX IF NOT EXISTS idx_migration_documents_page_num     ON migration_documents(page_number);
CREATE INDEX IF NOT EXISTS idx_migration_documents_batch_page    ON migration_documents(batch_id, page_number);

CREATE TABLE IF NOT EXISTS migration_reviews (
    id                   BIGSERIAL       PRIMARY KEY,
    document_id          BIGINT          NOT NULL REFERENCES migration_documents(id) ON DELETE CASCADE,
    reviewer_id          BIGINT          REFERENCES employees(id) ON DELETE SET NULL,
    review_status        VARCHAR(30)     NOT NULL,
    corrected_data_json  TEXT,
    remarks              VARCHAR(1000),
    reviewed_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_reviews_document ON migration_reviews(document_id);
CREATE INDEX IF NOT EXISTS idx_migration_reviews_reviewer ON migration_reviews(reviewer_id);

CREATE TABLE IF NOT EXISTS migration_imports (
    id                   BIGSERIAL       PRIMARY KEY,
    document_id          BIGINT          NOT NULL REFERENCES migration_documents(id) ON DELETE RESTRICT,
    customer_mobile      VARCHAR(15)     REFERENCES customers(customer_mobile) ON DELETE SET NULL,
    order_id             BIGINT          REFERENCES customer_orders(id) ON DELETE SET NULL,
    measurement_id       BIGINT          REFERENCES order_measurements(id) ON DELETE SET NULL,
    import_status        VARCHAR(30)     NOT NULL,
    error_message        VARCHAR(1000),
    imported_by          BIGINT          REFERENCES employees(id) ON DELETE SET NULL,
    imported_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_imports_document ON migration_imports(document_id);
CREATE INDEX IF NOT EXISTS idx_migration_imports_customer ON migration_imports(customer_mobile);
CREATE INDEX IF NOT EXISTS idx_migration_imports_order    ON migration_imports(order_id);