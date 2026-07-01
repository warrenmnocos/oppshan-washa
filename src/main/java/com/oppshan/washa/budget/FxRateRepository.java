package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The {@link FxRate} table: the global household FX snapshot (units of quote per 1 base), keyed by
 * the composite {@link FxRateId} of base and quote currency. It's a single shared table, not scoped
 * per user or per month. The one finder pulls every quote for a base currency: the rows a currency
 * converter or the user-editable rate map is built from. A live recompute works off the in-flight
 * slider rates instead and never reads this table.
 */
@Repository
public interface FxRateRepository
        extends CrudRepository<FxRate, FxRateId>, StatefulWriteRepository<FxRate> {

    /**
     * Rates quoted against a base currency. Filtering on {@code id.baseCurrency} uses the leading
     * column of the composite primary key, so this is index-backed (no full-table scan).
     */
    @Query("SELECT f FROM FxRate f WHERE f.id.baseCurrency = :baseCurrency")
    List<FxRate> findByBaseCurrency(@NotEmpty String baseCurrency);
}
