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
 * <p>Rates are user-editable: {@link #setRate(String, String, BigDecimal)} upserts a row, so a
 * slider edit or an applied "use market" value persists relationally. The live market quote itself
 * is fetched client-side (keyless currency-api / open.er-api) — the Lambda makes no outbound call.
 */
@Transactional
@ApplicationScoped
public class FxService {

    static final String CONSERVATIVE_BASE = "JPY";
    static final String CONSERVATIVE_QUOTE = "PHP";
    static final BigDecimal CONSERVATIVE_RATE = new BigDecimal("0.36");

    private final FxRateRepository fxRateRepository;

    @Inject
    public FxService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    public Map<String, BigDecimal> rates(String base) {
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
     * one exists, otherwise inserts a fresh snapshot. Returns the refreshed rate map for {@code base}
     * so the caller sees the same shape {@link #rates(String)} returns.
     */
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

        // Flush the stateful-session write so the subsequent rates() read (a Jakarta Data query that
        // may run on a separate session) sees the upserted row rather than the pre-write snapshot.
        fxRateRepository.flushWithSession();

        return rates(base);
    }
}
