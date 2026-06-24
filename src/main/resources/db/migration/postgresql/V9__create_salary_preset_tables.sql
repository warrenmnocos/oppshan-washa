-- Persistent, shared salary presets: payroll templates a user loads into the salary dialog. Mirrors
-- the income aggregate's payroll shape (component/deduction/variable/bracket), minus the budget-month
-- relationship — a preset is standalone. The four built-ins (jp, jp0, ph, blank) are seeded on startup
-- by SalaryPresetBootstrap; users may save and delete their own (built_in = FALSE). Enum columns store
-- the UPPER_CASE name() via @Enumerated(STRING) with a CHECK over the UPPER set (matching V5/V7); JSON
-- exchange uses the lowercase @JsonValue. Every table carries created_at + last_modified_at (the latter
-- is the JPA @Version).

CREATE TABLE salary_preset (
    uuid             UUID         NOT NULL,
    name             VARCHAR(255) NOT NULL,
    built_in         BOOLEAN      NOT NULL DEFAULT FALSE,
    currency         VARCHAR(3)   NOT NULL,
    engine           VARCHAR(64)  NOT NULL DEFAULT 'generic',
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_salary_preset PRIMARY KEY (uuid)
);
CREATE INDEX idx_salary_preset_name ON salary_preset (name);

-- Preset components: fixed pay lines.
CREATE TABLE salary_preset_component (
    uuid               UUID          NOT NULL,
    salary_preset_uuid UUID          NOT NULL,
    ordinal            INTEGER       NOT NULL DEFAULT 0,
    label              VARCHAR(255)  NOT NULL,
    amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    taxable            BOOLEAN       NOT NULL DEFAULT TRUE,
    basic              BOOLEAN       NOT NULL DEFAULT FALSE,
    var_name           VARCHAR(64),
    var_auto           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ   NOT NULL,
    last_modified_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_salary_preset_component PRIMARY KEY (uuid),
    CONSTRAINT fk_salary_preset_component_preset FOREIGN KEY (salary_preset_uuid)
        REFERENCES salary_preset (uuid) ON DELETE CASCADE
);
CREATE INDEX idx_salary_preset_component_preset_uuid ON salary_preset_component (salary_preset_uuid);

-- Preset deductions: polymorphic computation rules.
CREATE TABLE salary_preset_deduction (
    uuid               UUID          NOT NULL,
    salary_preset_uuid UUID          NOT NULL,
    ordinal            INTEGER       NOT NULL DEFAULT 0,
    label              VARCHAR(255)  NOT NULL,
    type               VARCHAR(16)   NOT NULL DEFAULT 'FIXED',
    base               VARCHAR(16),
    base_var           VARCHAR(64),
    rate               NUMERIC(9,4),
    cap                NUMERIC(18,4),
    floor_amount       NUMERIC(18,4),
    amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    expr               TEXT,
    fn                 VARCHAR(32),
    pretax             BOOLEAN       NOT NULL DEFAULT FALSE,
    var_name           VARCHAR(64),
    var_auto           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ   NOT NULL,
    last_modified_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_salary_preset_deduction PRIMARY KEY (uuid),
    CONSTRAINT fk_salary_preset_deduction_preset FOREIGN KEY (salary_preset_uuid)
        REFERENCES salary_preset (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_salary_preset_deduction_type
        CHECK (type IN ('PCT', 'FIXED', 'FORMULA', 'BRACKETS'))
);
CREATE INDEX idx_salary_preset_deduction_preset_uuid ON salary_preset_deduction (salary_preset_uuid);

-- Preset variables: named intermediate computations (no pretax).
CREATE TABLE salary_preset_variable (
    uuid               UUID          NOT NULL,
    salary_preset_uuid UUID          NOT NULL,
    ordinal            INTEGER       NOT NULL DEFAULT 0,
    var_name           VARCHAR(64)   NOT NULL,
    label              VARCHAR(255),
    type               VARCHAR(16)   NOT NULL DEFAULT 'FORMULA',
    base               VARCHAR(16),
    base_var           VARCHAR(64),
    rate               NUMERIC(9,4),
    cap                NUMERIC(18,4),
    floor_amount       NUMERIC(18,4),
    amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    expr               TEXT,
    var_auto           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ   NOT NULL,
    last_modified_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_salary_preset_variable PRIMARY KEY (uuid),
    CONSTRAINT fk_salary_preset_variable_preset FOREIGN KEY (salary_preset_uuid)
        REFERENCES salary_preset (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_salary_preset_variable_type
        CHECK (type IN ('PCT', 'FIXED', 'FORMULA', 'BRACKETS'))
);
CREATE INDEX idx_salary_preset_variable_preset_uuid ON salary_preset_variable (salary_preset_uuid);

-- Bracket rows belong to exactly one parent: a preset deduction OR a preset variable.
CREATE TABLE salary_preset_bracket (
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
    CONSTRAINT pk_salary_preset_bracket PRIMARY KEY (uuid),
    CONSTRAINT fk_salary_preset_bracket_deduction FOREIGN KEY (deduction_uuid)
        REFERENCES salary_preset_deduction (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_salary_preset_bracket_variable FOREIGN KEY (variable_uuid)
        REFERENCES salary_preset_variable (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_salary_preset_bracket_one_parent CHECK (
        (deduction_uuid IS NOT NULL AND variable_uuid IS NULL) OR
        (deduction_uuid IS NULL AND variable_uuid IS NOT NULL)),
    CONSTRAINT ck_salary_preset_bracket_op
        CHECK (op IN ('GT', 'GTE', 'LT', 'LTE', 'EQ')),
    CONSTRAINT ck_salary_preset_bracket_type
        CHECK (type IN ('FIXED', 'FORMULA', 'PCTGROSS', 'PCTBASIC'))
);
CREATE INDEX idx_salary_preset_bracket_deduction_uuid ON salary_preset_bracket (deduction_uuid);
CREATE INDEX idx_salary_preset_bracket_variable_uuid ON salary_preset_bracket (variable_uuid);
