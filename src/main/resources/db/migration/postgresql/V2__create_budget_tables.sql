-- Budget app: monthly-snapshot model. budget_month is the parent; income/expense/goal/debt
-- belong to it. The salary payroll engine (components, deductions, variables, brackets) is fully
-- normalized. Every table carries created_at + last_modified_at (the latter is the JPA @Version).

CREATE TABLE budget_month (
    uuid             UUID          NOT NULL,
    year_month       CHAR(7)       NOT NULL,
    base_currency    VARCHAR(3)    NOT NULL,
    fx_rate          NUMERIC(18,4),
    last_modified_by UUID,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_budget_month PRIMARY KEY (uuid),
    CONSTRAINT uc_budget_month_year_month UNIQUE (year_month)
);

CREATE TABLE income (
    uuid              UUID         NOT NULL,
    budget_month_uuid UUID         NOT NULL,
    ordinal           INTEGER      NOT NULL DEFAULT 0,
    name              VARCHAR(255) NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    engine            VARCHAR(64)  NOT NULL DEFAULT 'generic',
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_income PRIMARY KEY (uuid),
    CONSTRAINT fk_income_budget_month FOREIGN KEY (budget_month_uuid)
        REFERENCES budget_month (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_income_budget_month_uuid ON income (budget_month_uuid);

-- Payroll components: fixed pay lines.
CREATE TABLE income_component (
    uuid             UUID          NOT NULL,
    income_uuid      UUID          NOT NULL,
    ordinal          INTEGER       NOT NULL DEFAULT 0,
    label            VARCHAR(255)  NOT NULL,
    amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    taxable          BOOLEAN       NOT NULL DEFAULT TRUE,
    basic            BOOLEAN       NOT NULL DEFAULT FALSE,
    var_name         VARCHAR(64),
    var_auto         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_income_component PRIMARY KEY (uuid),
    CONSTRAINT fk_income_component_income FOREIGN KEY (income_uuid)
        REFERENCES income (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_income_component_income_uuid ON income_component (income_uuid);

-- Payroll deductions: polymorphic computation rules.
CREATE TABLE income_deduction (
    uuid             UUID          NOT NULL,
    income_uuid      UUID          NOT NULL,
    ordinal          INTEGER       NOT NULL DEFAULT 0,
    label            VARCHAR(255)  NOT NULL,
    kind             VARCHAR(16)   NOT NULL DEFAULT 'fixed',
    base             VARCHAR(16),
    base_var         VARCHAR(64),
    rate             NUMERIC(9,4),
    cap              NUMERIC(18,4),
    floor_amount     NUMERIC(18,4),
    amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    expr             TEXT,
    fn               VARCHAR(32),
    pretax           BOOLEAN       NOT NULL DEFAULT FALSE,
    var_name         VARCHAR(64),
    var_auto         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_income_deduction PRIMARY KEY (uuid),
    CONSTRAINT fk_income_deduction_income FOREIGN KEY (income_uuid)
        REFERENCES income (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_income_deduction_kind
        CHECK (kind IN ('pct', 'brackets', 'formula', 'fixed', 'computed'))
);
CREATE INDEX idx_income_deduction_income_uuid ON income_deduction (income_uuid);

-- Payroll variables: named intermediate computations (no pretax).
CREATE TABLE income_variable (
    uuid             UUID          NOT NULL,
    income_uuid      UUID          NOT NULL,
    ordinal          INTEGER       NOT NULL DEFAULT 0,
    var_name         VARCHAR(64)   NOT NULL,
    label            VARCHAR(255),
    kind             VARCHAR(16)   NOT NULL DEFAULT 'formula',
    base             VARCHAR(16),
    base_var         VARCHAR(64),
    rate             NUMERIC(9,4),
    cap              NUMERIC(18,4),
    floor_amount     NUMERIC(18,4),
    amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    expr             TEXT,
    var_auto         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_income_variable PRIMARY KEY (uuid),
    CONSTRAINT fk_income_variable_income FOREIGN KEY (income_uuid)
        REFERENCES income (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_income_variable_kind
        CHECK (kind IN ('pct', 'brackets', 'formula', 'fixed', 'computed'))
);
CREATE INDEX idx_income_variable_income_uuid ON income_variable (income_uuid);

-- Bracket rows belong to exactly one parent: a deduction OR a variable.
CREATE TABLE salary_bracket (
    uuid             UUID          NOT NULL,
    deduction_uuid   UUID,
    variable_uuid    UUID,
    ordinal          INTEGER       NOT NULL DEFAULT 0,
    var_name         VARCHAR(64),
    op               VARCHAR(8),
    val              NUMERIC(18,4),
    type             VARCHAR(16),
    rate             NUMERIC(9,4),
    expr             TEXT,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_salary_bracket PRIMARY KEY (uuid),
    CONSTRAINT fk_salary_bracket_deduction FOREIGN KEY (deduction_uuid)
        REFERENCES income_deduction (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_salary_bracket_variable FOREIGN KEY (variable_uuid)
        REFERENCES income_variable (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_salary_bracket_one_parent CHECK (
        (deduction_uuid IS NOT NULL AND variable_uuid IS NULL) OR
        (deduction_uuid IS NULL AND variable_uuid IS NOT NULL))
);
CREATE INDEX idx_salary_bracket_deduction_uuid ON salary_bracket (deduction_uuid);
CREATE INDEX idx_salary_bracket_variable_uuid ON salary_bracket (variable_uuid);

CREATE TABLE expense (
    uuid              UUID          NOT NULL,
    budget_month_uuid UUID          NOT NULL,
    ordinal           INTEGER       NOT NULL DEFAULT 0,
    label             VARCHAR(255)  NOT NULL,
    amount            NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)    NOT NULL,
    auto              VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL,
    last_modified_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_expense PRIMARY KEY (uuid),
    CONSTRAINT fk_expense_budget_month FOREIGN KEY (budget_month_uuid)
        REFERENCES budget_month (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_expense_budget_month_uuid ON expense (budget_month_uuid);

CREATE TABLE goal (
    uuid              UUID          NOT NULL,
    budget_month_uuid UUID          NOT NULL,
    ordinal           INTEGER       NOT NULL DEFAULT 0,
    label             VARCHAR(255)  NOT NULL,
    amount            NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)    NOT NULL,
    target_type       VARCHAR(16)   NOT NULL DEFAULT 'open',
    target_amount     NUMERIC(18,4),
    target_base       VARCHAR(32),
    target_mult       NUMERIC(18,4),
    savings           BOOLEAN       NOT NULL DEFAULT FALSE,
    withdrawal        NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    last_modified_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_goal PRIMARY KEY (uuid),
    CONSTRAINT fk_goal_budget_month FOREIGN KEY (budget_month_uuid)
        REFERENCES budget_month (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_goal_target_type CHECK (target_type IN ('open', 'amount', 'relative'))
);
CREATE INDEX idx_goal_budget_month_uuid ON goal (budget_month_uuid);

CREATE TABLE debt (
    uuid              UUID          NOT NULL,
    budget_month_uuid UUID          NOT NULL,
    ordinal           INTEGER       NOT NULL DEFAULT 0,
    name              VARCHAR(255)  NOT NULL,
    principal         NUMERIC(18,4) NOT NULL DEFAULT 0,
    annual_rate       NUMERIC(9,4)  NOT NULL DEFAULT 0,
    monthly           NUMERIC(18,4) NOT NULL DEFAULT 0,
    term_months       INTEGER,
    reprice_mode      VARCHAR(16),
    currency          VARCHAR(3)    NOT NULL,
    prepay            BOOLEAN       NOT NULL DEFAULT FALSE,
    prepay_amount     NUMERIC(18,4) NOT NULL DEFAULT 0,
    prepay_currency   VARCHAR(3),
    created_at        TIMESTAMPTZ   NOT NULL,
    last_modified_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_debt PRIMARY KEY (uuid),
    CONSTRAINT fk_debt_budget_month FOREIGN KEY (budget_month_uuid)
        REFERENCES budget_month (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_debt_budget_month_uuid ON debt (budget_month_uuid);

-- rateSteps are {afterYears, rate}: the rate applies from loan month afterYears*12 + 1.
CREATE TABLE debt_rate_step (
    uuid             UUID          NOT NULL,
    debt_uuid        UUID          NOT NULL,
    ordinal          INTEGER       NOT NULL DEFAULT 0,
    after_years      NUMERIC(5,2)  NOT NULL,
    rate             NUMERIC(9,4)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_debt_rate_step PRIMARY KEY (uuid),
    CONSTRAINT fk_debt_rate_step_debt FOREIGN KEY (debt_uuid)
        REFERENCES debt (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_debt_rate_step_debt_uuid ON debt_rate_step (debt_uuid);

-- The currency list is ordered; ordinal 0 is the base currency (JPY).
CREATE TABLE currency_setting (
    code             VARCHAR(3)  NOT NULL,
    ordinal          INTEGER     NOT NULL DEFAULT 0,
    symbol           VARCHAR(8)  NOT NULL,
    decimals         SMALLINT    NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_currency_setting PRIMARY KEY (code)
);

CREATE TABLE fx_rate (
    base_currency    VARCHAR(3)    NOT NULL,
    quote_currency   VARCHAR(3)    NOT NULL,
    rate             NUMERIC(18,8) NOT NULL,
    captured_at      TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    last_modified_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_fx_rate PRIMARY KEY (base_currency, quote_currency)
);
