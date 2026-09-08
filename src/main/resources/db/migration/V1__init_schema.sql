-- ============================================================================
-- V1 â€” Ritham ERP Unified Baseline Schema
-- Author : Ritham ERP Architecture
-- ============================================================================

-- â”€â”€ 1. Security & RBAC â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE departments (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE employees (
    id              BIGSERIAL    PRIMARY KEY,
    employee_code   VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    mobile_number   VARCHAR(15)  NOT NULL UNIQUE,
    email           VARCHAR(100),
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role_id         BIGINT       NOT NULL REFERENCES roles(id),
    department_id   BIGINT       REFERENCES departments(id),
    stage           VARCHAR(50),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_employees_username ON employees(username);
CREATE INDEX idx_employees_mobile   ON employees(mobile_number);
CREATE INDEX idx_employees_role     ON employees(role_id);
CREATE INDEX idx_employees_stage    ON employees(stage);

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT    NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    token        TEXT      NOT NULL UNIQUE,
    expires_at   TIMESTAMP NOT NULL,
    is_revoked   BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_employee ON refresh_tokens(employee_id);
CREATE INDEX idx_refresh_tokens_token    ON refresh_tokens(token);

-- â”€â”€ 2. Customers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE customers (
    customer_mobile VARCHAR(15)  PRIMARY KEY,
    customer_name   VARCHAR(100) NOT NULL,
    total_orders    INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_name ON customers(customer_name);

-- â”€â”€ 3. Customer Measurements (Directly Linked to Mobile Number) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE customer_measurements (
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

CREATE INDEX idx_cust_msr_mobile  ON customer_measurements(customer_mobile);
CREATE INDEX idx_cust_msr_garment ON customer_measurements(garment_type);

-- â”€â”€ 4. Customer Orders â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE customer_orders (
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
    receiver_name          VARCHAR(100),
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer_mobile ON customer_orders(customer_mobile);
CREATE INDEX idx_orders_status          ON customer_orders(status);
CREATE INDEX idx_orders_delivery_date   ON customer_orders(delivery_date);
CREATE INDEX idx_orders_employee        ON customer_orders(assigned_employee_id);

-- â”€â”€ 5. Order Sub-Entities â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE order_designs (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    design_name VARCHAR(150),
    notes       TEXT,
    image_url   TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_designs_order ON order_designs(order_id);

CREATE TABLE order_measurements (
    id                BIGSERIAL    PRIMARY KEY,
    order_id          BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    garment_type      VARCHAR(100) NOT NULL,
    measurements_json TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_measurements_order ON order_measurements(order_id);

CREATE TABLE order_materials (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    material_source VARCHAR(20)  NOT NULL DEFAULT 'COMPANY',
    material_name   VARCHAR(150) NOT NULL,
    quantity        NUMERIC(10,2),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_materials_order ON order_materials(order_id);

CREATE TABLE production_tracking (
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

CREATE INDEX idx_production_order    ON production_tracking(order_id);
CREATE INDEX idx_production_employee ON production_tracking(employee_id);
CREATE INDEX idx_production_stage    ON production_tracking(production_stage);

CREATE TABLE order_payments (
    id             BIGSERIAL     PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    payment_type   VARCHAR(20)   NOT NULL,
    payment_method VARCHAR(30)   NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    payment_date   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON order_payments(order_id);

CREATE TABLE order_deliveries (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT    NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    delivery_date   TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_by    BIGINT    REFERENCES employees(id),
    is_delivered    BOOLEAN   NOT NULL DEFAULT FALSE,
    receipt_printed BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_deliveries_order ON order_deliveries(order_id);

-- â”€â”€ 6. Production Stages Master (12 Tailoring Stages) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE production_stages (
    id            BIGSERIAL    PRIMARY KEY,
    stage_key     VARCHAR(50)  NOT NULL UNIQUE,
    title         VARCHAR(100) NOT NULL,
    description   VARCHAR(255),
    icon          VARCHAR(10)  DEFAULT 'ðŸ§µ',
    display_order INT          NOT NULL,
    color         VARCHAR(20)  DEFAULT '#818CF8',
    bg_color      VARCHAR(50)  DEFAULT 'rgba(99, 102, 241, 0.15)',
    is_active     BOOLEAN      DEFAULT TRUE,
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_stage_order  ON production_stages(display_order);
CREATE INDEX idx_stage_active ON production_stages(is_active);