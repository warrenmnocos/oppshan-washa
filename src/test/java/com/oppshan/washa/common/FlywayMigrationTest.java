package com.oppshan.washa.common;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Locks Flyway-migration ↔ entity parity by reproducing the production startup path against a
 * Dev Services Postgres: apply the real V1–V9 migrations, then let Hibernate validate the mapped
 * entities against the Flyway-built schema. The booting context is itself the parity proof — under
 * {@code schema-management.strategy=validate} with {@code halt-on-error=true}, any column / type /
 * constraint drift between an entity and the migrated schema halts the context (this is what
 * surfaced the {@code year_month} CHAR/VARCHAR mismatch fixed in V4). The Flyway assertions below
 * add the complementary guarantee that every versioned migration on the classpath actually ran.
 *
 * <p>The {@link MigrateAndValidateProfile} pins this prod path explicitly so the contract holds even
 * if a future {@code %test} override ever flips the default schema generation to {@code drop-and-create}
 * (which would generate the schema straight from the entities and never exercise the migrations).
 */
@QuarkusTest
@TestProfile(FlywayMigrationTest.MigrateAndValidateProfile.class)
class FlywayMigrationTest {

    private static final String MIGRATIONS_CLASSPATH_LOCATION = "db/migration/postgresql";

    private static final Pattern VERSIONED_MIGRATION_FILENAME = Pattern.compile("^V\\d+__.+\\.sql$");

    @Inject
    Flyway flyway;

    @Test
    void shouldApplyEveryVersionedMigrationAndValidateEntitiesAgainstTheSchema() throws IOException {
        // Reaching this assertion already means the Quarkus context booted under Flyway migrate +
        // Hibernate validate, i.e. the entities match the migrated schema with no drift.
        final var classpathFilenames = listClasspathVersionedMigrationFilenames();
        final var info = flyway.info();
        // Count only versioned migrations. When create-schemas provisions the `washa` schema on a
        // fresh database, Flyway records a synthetic "<< Flyway Schema Creation >>" row with a null
        // version; it must not be counted against the V-numbered files (it is absent when the schema
        // already exists, e.g. a reused Dev Services container).
        final var appliedMigrations = Arrays.stream(info.applied())
                .filter(migration -> migration.getVersion() != null)
                .toList();
        final var pendingMigrations = Arrays.asList(info.pending());

        assertThat("Versioned migration files must exist on the classpath",
                classpathFilenames.size(), is(greaterThan(0)));

        assertThat("No migration may be pending after migrate-at-start",
                pendingMigrations, is(empty()));

        assertThat("Applied migration count must match versioned files on the classpath",
                appliedMigrations.size(), is(equalTo(classpathFilenames.size())));

        for (final var migration : appliedMigrations) {
            final var label = "V" + migration.getVersion() + "__" + migration.getDescription();
            assertThat(label + " must have SUCCESS state",
                    migration.getState(), is(equalTo(MigrationState.SUCCESS)));
        }
    }

    private static List<String> listClasspathVersionedMigrationFilenames() throws IOException {
        final var resourceUrl =
                FlywayMigrationTest.class.getClassLoader().getResource(MIGRATIONS_CLASSPATH_LOCATION);
        if (resourceUrl == null) {
            throw new IllegalStateException(
                    "Classpath resource " + MIGRATIONS_CLASSPATH_LOCATION + " is missing"
            );
        }
        try (final var stream = Files.list(Path.of(URI.create(resourceUrl.toString())))) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> VERSIONED_MIGRATION_FILENAME.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    /**
     * Forces the production schema lifecycle in the test context: Flyway runs the real migrations at
     * start and Hibernate only validates against them, instead of generating the schema from the
     * entities. Both the current Quarkus property ({@code schema-management.strategy}) and its
     * deprecated alias ({@code database.generation}) are set to {@code validate} so the override holds
     * regardless of which one a future config flips.
     */
    public static class MigrateAndValidateProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flyway.migrate-at-start", "true",
                    "quarkus.hibernate-orm.schema-management.strategy", "validate",
                    "quarkus.hibernate-orm.schema-management.halt-on-error", "true",
                    "quarkus.hibernate-orm.database.generation", "validate"
            );
        }
    }
}
