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
 *
 * <p>Writes go through the repositories' {@code insertWithSession} (managed-session persist so the
 * {@code @UuidGenerator} runs) — no direct {@code EntityManager} use.
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
        for (final var person : AllowedIdentitiesParser.parse(json)) {
            final var personUuid = resolvePersonUuid(person);
            for (final var email : person.emails()) {
                final var normalized = normalize(email);
                if (allowedIdentityRepository.findByEmail(normalized).isEmpty()) {
                    allowedIdentityRepository.insertWithSession(new AllowedIdentity()
                            .setEmail(normalized)
                            .setUserAccountUuid(personUuid));
                }
            }
        }
    }

    private UUID resolvePersonUuid(Person person) {
        // Reuse the person already linked to any of their emails; else create a new UserAccount.
        for (final var email : person.emails()) {
            final var existing = allowedIdentityRepository.findByEmail(normalize(email));
            if (existing.isPresent()) {
                return existing.get().getUserAccountUuid();
            }
        }
        final var user = new UserAccount()
                .setFirstName(person.firstName())
                .setLastName(person.lastName());
        userAccountRepository.insertWithSession(user); // assigns the VERSION_7 uuid
        return user.getUuid();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
