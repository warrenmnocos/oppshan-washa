package com.oppshan.washa.user;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

import java.util.UUID;

@Repository
public interface UserAccountRepository
        extends CrudRepository<UserAccount, UUID>, StatefulWriteRepository<UserAccount> {
}
