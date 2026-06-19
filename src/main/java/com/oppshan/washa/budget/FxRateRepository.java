package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

@Repository
public interface FxRateRepository
        extends CrudRepository<FxRate, FxRateId>, StatefulWriteRepository<FxRate> {
}
