package com.oppshan.washa.config;

import com.oppshan.washa.user.AllowedIdentity;
import com.oppshan.washa.user.AllowedIdentityRepository;
import com.oppshan.washa.user.UserAccount;
import com.oppshan.washa.user.UserAccountRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Seeds the two household people and the email allowlist from Parameter Store
 * ({@code washa.allowed-identities}) on startup. Idempotent — re-running adds nothing new.
 * The Google {@code sub} is captured later, on first login.
 */
@ApplicationScoped
public class IdentityBootstrap {

    private final AllowedIdentitiesConfig config;
    private final AllowedIdentityRepository allowedIdentityRepository;
    private final UserAccountRepository userAccountRepository;

    @Inject
    public IdentityBootstrap(AllowedIdentitiesConfig config,
                             AllowedIdentityRepository allowedIdentityRepository,
                             UserAccountRepository userAccountRepository) {
        this.config = config;
        this.allowedIdentityRepository = allowedIdentityRepository;
        this.userAccountRepository = userAccountRepository;
    }

    void onStart(@Observes StartupEvent event) {
        seed(config.allowedIdentities());
    }

    @Transactional
    public void seed(String json) {
        for (AllowedIdentitiesParser.Person person : AllowedIdentitiesParser.parse(json)) {
            UUID personUuid = resolvePersonUuid(person);
            for (String email : person.emails()) {
                String normalized = normalize(email);
                if (allowedIdentityRepository.findByEmail(normalized).isEmpty()) {
                    allowedIdentityRepository.save(new AllowedIdentity()
                            .setEmail(normalized)
                            .setUserAccountUuid(personUuid));
                }
            }
        }
    }

    private UUID resolvePersonUuid(AllowedIdentitiesParser.Person person) {
        // Reuse the person already linked to any of their emails; else create a new UserAccount.
        for (String email : person.emails()) {
            var existing = allowedIdentityRepository.findByEmail(normalize(email));
            if (existing.isPresent()) {
                return existing.get().getUserAccountUuid();
            }
        }
        UserAccount saved = userAccountRepository.save(new UserAccount()
                .setFirstName(person.firstName())
                .setLastName(person.lastName()));
        return saved.getUuid();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
