package com.oppshan.washa.budget;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies FX rates (units of a quote currency per one base unit) for the budget. Reads the
 * snapshotted {@code fx_rate} table; when nothing is stored it falls back to the deliberately
 * conservative planning default (¥1 = ₱0.36, HANDOVER §10).
 *
 * <p>Live refresh from the keyless public sources (currency-api / open.er-api) is a follow-up;
 * until then the conservative default keeps planning math stable.
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
}
