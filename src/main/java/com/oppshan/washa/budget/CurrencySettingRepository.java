package com.oppshan.washa.budget;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

@Repository
public interface CurrencySettingRepository extends CrudRepository<CurrencySetting, String> {
}
