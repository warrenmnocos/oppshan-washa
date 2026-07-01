package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;

/**
 * Repository for the sign-in allowlist ({@link AllowedIdentity}), keyed by the email natural PK
 * ({@code String}). Its finder looks an entry up by that email key.
 */
@Repository
public interface AllowedIdentityRepository
        extends CrudRepository<AllowedIdentity, String>, StatefulWriteRepository<AllowedIdentity> {

    /**
     * Looks up an allowlist entry by its email key. Expects an already-normalized email (trimmed,
     * lower-cased): the table stores normalized addresses and this is an exact match, so a
     * differently-cased input silently misses.
     */
    @Find
    Optional<AllowedIdentity> findByEmail(@NotEmpty String email);
}
