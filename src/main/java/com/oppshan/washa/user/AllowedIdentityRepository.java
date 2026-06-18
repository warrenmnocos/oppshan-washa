package com.oppshan.washa.user;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;

@Repository
public interface AllowedIdentityRepository extends CrudRepository<AllowedIdentity, String> {

    @Find
    Optional<AllowedIdentity> findByEmail(@NotEmpty String email);
}
