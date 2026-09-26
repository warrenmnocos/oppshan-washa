package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class FxServiceTest {

    @Inject
    FxService fxService;

    @Inject
    FxRateRepository fxRateRepository;

    /**
     * Start of the per-classload currency-code sequence: a random base per load (future-proofs a reused test DB,
     * mirroring {@code BudgetServiceTest.BASE_YEAR}) stepped sequentially by {@link #nextCurrencyCode()}.
     */
    private static final AtomicInteger CODE_SEQUENCE =
            new AtomicInteger(ThreadLocalRandom.current().nextInt(20 * 20 * 20));

    /**
     * A fresh three-letter code per call, drawn from letters G–Z only. Sequential rather than random because this class
     * asserts whole-collection sizes on its bases, and birthday collisions among the 216 UUID-derived codes (hex
     * letters run a–f) actually broke that; G–Z also keeps clear of {@code BudgetEndpointTest}'s A–F codes. Two
     * hardenings on top of the raw sequence. J, P, Y and H all sit INSIDE G–Z, so the sequence can spell the literal
     * JPY and PHP codes — emitting them breaks the JPY-fallback assertions — and both are skipped outright. And each
     * candidate is DB-verified as an unused base before being handed out: Quarkus can initialise the test class more
     * than once per JVM run, and a re-rolled congruent sequence would otherwise re-issue codes an earlier load already
     * persisted, colliding on the (base, quote) primary key exactly like {@code BudgetServiceTest}'s year strides did.
     */
    private String nextCurrencyCode() {
        while (true) {
            final var sequence = CODE_SEQUENCE.getAndIncrement();
            final var candidate = String.valueOf((char) ('G' + sequence / 400 % 20))
                    + (char) ('G' + sequence / 20 % 20)
                    + (char) ('G' + sequence % 20);
            if (candidate.equals("JPY") || candidate.equals("PHP")) {
                continue;
            }

            final var baseUnused = QuarkusTransaction.requiringNew().call(() ->
                    fxRateRepository.findByBaseCurrency(candidate).isEmpty());
            if (baseUnused) {
                return candidate;
            }
        }
    }

    @Test
    void shouldFallBackToTheConservativeJpyToPhpRateWhenNothingIsStored() {
        clearStoredJpyRates();

        final var rates = fxService.rates("JPY");

        assertThat(rates, is(aMapWithSize(1)));
        assertThat(rates.get("PHP"), is(comparesEqualTo(new BigDecimal("0.36"))));

        // The fallback is computed into the returned map, never persisted — JPY stays "nothing stored",
        // so a later real quote still lands on the insert path rather than overwriting a phantom row.
        assertThat(fxRateRepository.findByBaseCurrency("JPY"), is(empty()));
    }

    @Test
    void shouldReturnAnEmptyMapForANonJpyBaseWithNothingStored() {
        // The conservative planning default is JPY-only; any other base with no stored rows comes back empty.
        final var rates = fxService.rates(nextCurrencyCode());

        assertThat(rates, is(anEmptyMap()));
    }

    @Test
    void shouldReturnEveryStoredRateForTheBaseKeyedByQuote() {
        final var base = nextCurrencyCode();
        final var firstQuote = nextCurrencyCode();
        final var secondQuote = nextCurrencyCode();
        seedRate(base, firstQuote, "0.36");
        seedRate(base, secondQuote, "171.50");

        final var rates = fxService.rates(base);

        assertThat(rates, is(aMapWithSize(2)));
        assertThat(rates.get(firstQuote), is(comparesEqualTo(new BigDecimal("0.36"))));
        assertThat(rates.get(secondQuote), is(comparesEqualTo(new BigDecimal("171.50"))));
    }

    @Test
    void shouldSuppressTheJpyFallbackOnceAnyJpyRateIsStored() {
        // Any stored JPY row — whatever its quote — replaces the synthesized JPY→PHP default outright.
        clearStoredJpyRates();
        final var quote = nextCurrencyCode(); // letters G–Z only, so never PHP itself
        seedRate("JPY", quote, "1.05");

        final var rates = fxService.rates("JPY");

        assertThat(rates, is(aMapWithSize(1)));
        assertThat(rates, not(hasKey("PHP")));
        assertThat(rates.get(quote), is(comparesEqualTo(new BigDecimal("1.05"))));
    }

    @Test
    void shouldInsertAFreshSnapshotOnFirstSetRateAndReturnTheRefreshedMap() {
        final var base = nextCurrencyCode();
        final var quote = nextCurrencyCode();

        final var rates = fxService.setRate(base, quote, new BigDecimal("0.42"));

        assertThat(rates.get(quote), is(comparesEqualTo(new BigDecimal("0.42"))));

        // The upsert persisted a real snapshot row (not just a map entry), stamped with its capture time.
        final var stored = fxRateRepository.findByBaseCurrency(base);
        assertThat(stored, hasSize(1));
        assertThat(stored.getFirst().getRate(), is(comparesEqualTo(new BigDecimal("0.42"))));
        assertThat(stored.getFirst().getCapturedAt(), is(notNullValue()));
    }

    @Test
    void shouldUpdateTheStoredRowOnASecondSetRateInsteadOfDuplicatingIt() {
        final var base = nextCurrencyCode();
        final var quote = nextCurrencyCode();
        fxService.setRate(base, quote, new BigDecimal("0.50"));

        final var rates = fxService.setRate(base, quote, new BigDecimal("0.75"));

        assertThat(rates.get(quote), is(comparesEqualTo(new BigDecimal("0.75"))));

        // One row per directed pair: the second upsert overwrote it. A duplicate would poison rates()'
        // quote-keyed map, which is exactly what the update-then-flush path exists to prevent.
        final var stored = fxRateRepository.findByBaseCurrency(base);
        assertThat(stored, hasSize(1));
        assertThat(stored.getFirst().getRate(), is(comparesEqualTo(new BigDecimal("0.75"))));
    }

    @Test
    void shouldRejectANonPositiveRateAtTheServiceBoundary() {
        final var base = nextCurrencyCode();
        final var quote = nextCurrencyCode();

        // @Positive on the signature is enforced by the container on the CDI proxy, so a bad rate never
        // reaches the upsert — nothing may be persisted for the pair.
        assertThrows(ConstraintViolationException.class, () -> fxService.setRate(base, quote, BigDecimal.ZERO));

        assertThat(fxRateRepository.findByBaseCurrency(base), is(empty()));
    }

    /**
     * Deletes every stored JPY-base rate so a test can assert the empty-table fallback deterministically — a saved
     * month or an earlier test in the run may have persisted JPY rates (A.10: control shared DB state).
     */
    private void clearStoredJpyRates() {
        QuarkusTransaction.requiringNew().run(() -> fxRateRepository.findByBaseCurrency("JPY").forEach(storedJpyRate ->
                fxRateRepository.deleteWithSession(fxRateRepository.attachWithSession(storedJpyRate))
        ));
    }

    /**
     * Inserts one rate row directly through the repository, bypassing the service under test. Callers pass fresh
     * per-run-unique pairs (or a just-cleared JPY base), so a plain insert never collides.
     */
    private void seedRate(String base, String quote, String rate) {
        QuarkusTransaction.requiringNew().run(() -> fxRateRepository.insertWithSession(new FxRate()
                .setId(new FxRateId(base, quote))
                .setRate(new BigDecimal(rate))
                .setCapturedAt(Instant.now())
        ));
    }
}
