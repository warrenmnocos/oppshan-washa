package com.oppshan.washa.common;

import com.oppshan.washa.budget.BudgetMonth;
import com.oppshan.washa.budget.BudgetMonthRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@link StatefulWriteRepository} mixin end-to-end through a real repository:
 * insert, attach, update, flush, delete against Dev Services PostgreSQL.
 */
@QuarkusTest
class StatefulWriteRepositoryTest {

    @Inject
    BudgetMonthRepository repository;

    @Test
    void shouldInsertUpdateAndDeleteThroughTheMixin() {
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(YearMonth.of(2031, 1)).setBaseCurrency("JPY");
            repository.insertWithSession(month);
            repository.flushWithSession();
            assertThat(month.getUuid()).isNotNull();
        });

        // Update a detached instance via merge (attach/update return the managed copy).
        QuarkusTransaction.requiringNew().run(() -> {
            final var loaded = repository.findByYearMonth(YearMonth.of(2031, 1)).orElseThrow();
            final var managed = repository.updateWithSession(loaded.setBaseCurrency("PHP"));
            assertThat(managed.getBaseCurrency()).isEqualTo("PHP");
        });

        QuarkusTransaction.requiringNew().run(() ->
                assertThat(repository.findByYearMonth(YearMonth.of(2031, 1)).orElseThrow().getBaseCurrency())
                        .isEqualTo("PHP"));

        // Delete: attach the detached entity into the session, then remove it.
        QuarkusTransaction.requiringNew().run(() -> {
            final var loaded = repository.findByYearMonth(YearMonth.of(2031, 1)).orElseThrow();
            repository.deleteWithSession(repository.attachWithSession(loaded));
        });

        QuarkusTransaction.requiringNew().run(() ->
                assertThat(repository.findByYearMonth(YearMonth.of(2031, 1))).isEmpty());
    }
}
