package com.oppshan.washa.user;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdpAccountRepository extends CrudRepository<IdpAccount, UUID> {

    @Find
    Optional<IdpAccount> findByProviderNameAndProviderId(@NotEmpty String providerName,
                                                         @NotEmpty String providerId);
}
