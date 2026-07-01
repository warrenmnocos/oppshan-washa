package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.UUID;

/**
 * Repository for household people ({@link UserAccount}), keyed by UUID. It needs no custom finders:
 * a person is always reached by UUID (from an allowlist row or a linked identity), never looked up
 * by name, so {@code CrudRepository} plus the stateful-write mixin is the whole surface.
 */
@Repository
public interface UserAccountRepository
        extends CrudRepository<UserAccount, UUID>, StatefulWriteRepository<UserAccount> {
}
