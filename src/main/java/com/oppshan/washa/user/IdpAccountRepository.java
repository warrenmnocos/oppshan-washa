package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdpAccountRepository
        extends CrudRepository<IdpAccount, UUID>, StatefulWriteRepository<IdpAccount> {

    /** Resolves a Google identity by its stable subject, fetching the owning person for the view. */
    @Query("""
            SELECT g
            FROM GoogleAccount g
            LEFT JOIN FETCH g.userAccount
            WHERE g.providerName = :providerName AND g.providerId = :providerId""")
    Optional<GoogleAccount> findGoogleByProvider(@NotEmpty String providerName,
                                                 @NotEmpty String providerId);
}
