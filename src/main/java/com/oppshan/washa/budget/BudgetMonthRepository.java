package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetMonthRepository
        extends CrudRepository<BudgetMonth, UUID>, StatefulWriteRepository<BudgetMonth> {

    @Find
    Optional<BudgetMonth> findByYearMonth(@NotNull YearMonth yearMonth);
}
