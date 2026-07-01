package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for linked identities ({@link IdpAccount} and its subtypes), keyed by UUID. Its one
 * custom finder resolves a Google identity by its provider subject pair.
 */
@Repository
public interface IdpAccountRepository
        extends CrudRepository<IdpAccount, UUID>, StatefulWriteRepository<IdpAccount> {

    /**
     * Resolves a Google identity by its stable ({@code providerName}, {@code providerId}) subject
     * pair. Eagerly fetches the owning person via {@code LEFT JOIN FETCH} so the association is
     * loaded up front, sparing a follow-up lazy load.
     */
    @Query("""
            SELECT g
            FROM GoogleAccount g
            LEFT JOIN FETCH g.userAccount
            WHERE g.providerName = :providerName AND g.providerId = :providerId""")
    Optional<GoogleAccount> findGoogleByProvider(@NotEmpty String providerName,
                                                 @NotEmpty String providerId);
}
