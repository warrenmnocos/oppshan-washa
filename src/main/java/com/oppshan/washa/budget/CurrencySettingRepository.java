package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

/**
 * The {@link CurrencySetting} store: the single global household currency list (the prototype's
 * {@code cur}), keyed by three-letter code rather than a UUID, with {@code ordinal} 0 marking the
 * base currency. It's one shared list, not per-month or per-user, so nothing here filters by an
 * owning user. No custom finders are needed: plain CRUD by code ({@code findAll} to load,
 * {@code findById(code)} to upsert, delete for a dropped currency) plus the
 * {@link StatefulWriteRepository} mixin covers every read and write this list needs.
 */
@Repository
public interface CurrencySettingRepository
        extends CrudRepository<CurrencySetting, String>, StatefulWriteRepository<CurrencySetting> {
}
