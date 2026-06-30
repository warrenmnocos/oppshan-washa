package com.oppshan.washa.config;

import com.oppshan.washa.budget.SalaryPresetRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class SalaryPresetBootstrapTest {

    @Inject
    SalaryPresetBootstrap bootstrap;

    @Inject
    SalaryPresetRepository salaryPresetRepository;

    @Test
    void shouldSeedTheFourBuiltInsIdempotently() {
        // The app already ran the startup seed at boot; calling it again must not duplicate a
        // built-in or throw — existsBuiltInByName guards each insert.
        bootstrap.seed();
        bootstrap.seed();

        assertThat(builtInCount("Japan"), is(1L));
        assertThat(builtInCount("Japan No Resident Tax"), is(1L));
        assertThat(builtInCount("Philippines"), is(1L));
        assertThat(builtInCount("blank"), is(1L));

        assertThat(salaryPresetRepository.existsBuiltInByName("Japan"), is(true));
    }

    private long builtInCount(String name) {
        return QuarkusTransaction.requiringNew().call(() ->
                salaryPresetRepository.listOrdered().stream()
                        .filter(preset -> preset.isBuiltIn() && name.equals(preset.getName()))
                        .count());
    }
}
