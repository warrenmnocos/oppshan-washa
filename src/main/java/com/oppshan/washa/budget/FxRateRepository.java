package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

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
