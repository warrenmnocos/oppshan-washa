-- year_month is mapped by YearMonthStringConverter to a String attribute with @Column(length = 7),
-- which Hibernate validates as VARCHAR(7). V2 created it as CHAR(7) (reported by PostgreSQL as
-- bpchar), tripping post-boot schema validation. Align the column to the entity's mapping.
ALTER TABLE budget_month
    ALTER COLUMN year_month TYPE VARCHAR(7);
