package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;

import java.util.Optional;

@Repository
public interface AllowedIdentityRepository
        extends CrudRepository<AllowedIdentity, String>, StatefulWriteRepository<AllowedIdentity> {

    @Find
    Optional<AllowedIdentity> findByEmail(@NotEmpty String email);
}
