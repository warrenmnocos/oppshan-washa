package com.oppshan.washa.budget;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies FX rates (units of a quote currency per one base unit) for the budget. Reads the
 * snapshotted {@code fx_rate} table; when nothing is stored it falls back to the deliberately
 * conservative planning default (¥1 = ₱0.36, HANDOVER §10).
 *
 * <p>Rates are user-editable: {@link #setRate(String, String, BigDecimal)} upserts a row, so an
 * adjusted or "use market" rate persists relationally. Live market quotes are fetched outside this
 * service (keyless currency-api / open.er-api) and only handed in through {@code setRate}; the
 * service itself makes no outbound call.
 */
@Transactional
@ApplicationScoped
public class FxService {

    /**
     * Base currency of the single fallback rate used when the {@code fx_rate} table is empty. The
     * fallback only kicks in for a JPY base; see {@link #rates(String)}.
     */
    static final String CONSERVATIVE_BASE = "JPY";

    /** Quote currency of the fallback rate, so the empty-table default is JPY→PHP. */
    static final String CONSERVATIVE_QUOTE = "PHP";

    /** The fallback rate itself: ¥1 = ₱0.36, deliberately conservative for planning (HANDOVER §10). */
    static final BigDecimal CONSERVATIVE_RATE = new BigDecimal("0.36");

    private final FxRateRepository fxRateRepository;

    /** Injects the repository backing the {@code fx_rate} table. */
    @Inject
    public FxService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    /**
     * The stored rates for {@code base} as a quote→rate map (units of quote per one base unit). When
     * nothing is stored and {@code base} is JPY, seeds the single conservative JPY→PHP planning default
     * so a fresh install still has a usable rate; any other base with no stored rows returns empty.
     */
    @NotNull
    public Map<String, BigDecimal> rates(@NotEmpty String base) {
        final var ratesByQuote = new HashMap<String, BigDecimal>();
        for (final var fxRate : fxRateRepository.findByBaseCurrency(base)) {
            ratesByQuote.put(fxRate.getId().getQuoteCurrency(), fxRate.getRate());
        }

        if (ratesByQuote.isEmpty() && CONSERVATIVE_BASE.equals(base)) {
            ratesByQuote.put(CONSERVATIVE_QUOTE, CONSERVATIVE_RATE);
        }

        return ratesByQuote;
    }

    /**
     * Upserts the {@code base → quote} rate (units of quote per one base): updates the stored row if
     * one exists, otherwise inserts a fresh snapshot. Returns the refreshed rate map for {@code base},
     * the same shape {@link #rates(String)} returns.
     *
     * <p>It flushes the stateful-session write first so the following {@link #rates(String)} read (a
     * Jakarta Data query that may run on a separate session) sees the upserted row rather than the
     * pre-write snapshot.
     */
    @NotNull
    public Map<String, BigDecimal> setRate(@NotEmpty String base,
                                           @NotEmpty String quote,
                                           @NotNull @Positive BigDecimal rate) {
        final var id = new FxRateId(base, quote);
        fxRateRepository.findById(id)
                .map(fxRateRepository::attachWithSession)
                .map(existing -> fxRateRepository.updateWithSession(existing.setRate(rate)
                        .setCapturedAt(Instant.now())))
                .orElseGet(() -> fxRateRepository.insertWithSession(new FxRate()
                        .setId(id)
                        .setRate(rate)
                        .setCapturedAt(Instant.now())));

        fxRateRepository.flushWithSession();

        return rates(base);
    }
}
