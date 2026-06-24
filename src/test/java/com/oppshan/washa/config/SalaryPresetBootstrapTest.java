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
        // The app already ran the startup seed; re-running must not duplicate or throw (it also
        // exercises idempotency across restarts, since the reused test DB keeps the rows between runs).
        bootstrap.seed();
        bootstrap.seed();

        assertThat(builtInCount("jp"), is(1L));
        assertThat(builtInCount("jp0"), is(1L));
        assertThat(builtInCount("ph"), is(1L));
        assertThat(builtInCount("blank"), is(1L));

        assertThat(salaryPresetRepository.existsBuiltInByName("jp"), is(true));
    }

    private long builtInCount(String name) {
        return QuarkusTransaction.requiringNew().call(() ->
                salaryPresetRepository.listOrdered().stream()
                        .filter(preset -> preset.isBuiltIn() && name.equals(preset.getName()))
                        .count());
    }
}
