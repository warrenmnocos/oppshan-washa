package com.oppshan.washa.common;

import com.oppshan.washa.budget.BudgetMonth;
import com.oppshan.washa.budget.BudgetMonthRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
        // A per-run random month, DB-verified free before use (draw-until-free). A random draw alone is
        // not collision-proof however the year band is chosen: the shared Dev Services container can
        // carry rows from an overlapping IDE-launched run or an interrupted one (A.10), and this exact
        // test once hit a pre-existing row at its freshly drawn year.
        final var yearMonth = QuarkusTransaction.requiringNew().call(() -> {
            while (true) {
                final var candidate = YearMonth.of(ThreadLocalRandom.current().nextInt(8000, 9800), 1);
                if (repository.findByYearMonth(candidate).isEmpty()) {
                    return candidate;
                }
            }
        });
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency("JPY");
            repository.insertWithSession(month);
            repository.flushWithSession();
            assertThat(month.getUuid(), is(notNullValue()));
        });

        // Update a detached instance via merge (attach/update return the managed copy).
        QuarkusTransaction.requiringNew().run(() -> {
            final var loaded = repository.findByYearMonth(yearMonth).orElseThrow();
            final var managed = repository.updateWithSession(loaded.setBaseCurrency("PHP"));
            assertThat(managed.getBaseCurrency(), is("PHP"));
        });

        QuarkusTransaction.requiringNew().run(() ->
                assertThat(repository.findByYearMonth(yearMonth).orElseThrow().getBaseCurrency(),
                        is("PHP")));

        // Delete: attach the detached entity into the session, then remove it.
        QuarkusTransaction.requiringNew().run(() -> {
            final var loaded = repository.findByYearMonth(yearMonth).orElseThrow();
            repository.deleteWithSession(repository.attachWithSession(loaded));
        });

        QuarkusTransaction.requiringNew().run(() ->
                assertThat(repository.findByYearMonth(yearMonth).isEmpty(), is(true)));
    }
}
