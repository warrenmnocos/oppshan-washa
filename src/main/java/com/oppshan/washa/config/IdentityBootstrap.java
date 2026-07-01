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
 * Seeds the household people and their email allowlist from Parameter Store
 * ({@code oppshan.washa.allowed-identities}) on startup. Idempotent: an email already present is
 * skipped, so a restart or redeploy adds nothing new. It seeds only names and allow-listed emails;
 * the Google {@code sub} isn't part of the seed.
 *
 * <p>Writes go through the repositories' {@code insertWithSession}, a managed-session persist so the
 * {@code @UuidGenerator} assigns each new {@code UserAccount}'s VERSION_7 uuid. No direct
 * {@code EntityManager} use (backend CLAUDE.md A.4).
 */
@ApplicationScoped
public class IdentityBootstrap {

    private final AllowedIdentitiesConfig config;
    private final AllowedIdentityRepository allowedIdentityRepository;
    private final UserAccountRepository userAccountRepository;

    /** Injects the allowlist config and the account/identity repositories the seed writes through. */
    @Inject
    public IdentityBootstrap(AllowedIdentitiesConfig config,
                             AllowedIdentityRepository allowedIdentityRepository,
                             UserAccountRepository userAccountRepository) {
        this.config = config;
        this.allowedIdentityRepository = allowedIdentityRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /** Runs the seed once the container is up (CDI startup observer). */
    void onStart(@Observes StartupEvent event) {
        seed(config.allowedIdentities());
    }

    /**
     * Resolves (or creates) each configured person's {@code UserAccount}, then inserts an
     * {@code AllowedIdentity} for every one of their emails not already present. {@code @Transactional}
     * so the whole seed commits atomically; the {@code findByEmail} guard makes a second run a no-op.
     */
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

    /**
     * The {@code UserAccount} uuid a person's emails should point at. A person can list several emails;
     * if any is already allow-listed, reuse the account it links to, so re-running (or adding an email
     * to an existing person) never spawns a duplicate account. Otherwise create a fresh
     * {@code UserAccount}, whose {@code insertWithSession} persist assigns the VERSION_7 uuid.
     */
    private UUID resolvePersonUuid(Person person) {
        for (final var email : person.emails()) {
            final var existing = allowedIdentityRepository.findByEmail(normalize(email));
            if (existing.isPresent()) {
                return existing.get().getUserAccountUuid();
            }
        }
        final var user = new UserAccount()
                .setFirstName(person.firstName())
                .setLastName(person.lastName());
        userAccountRepository.insertWithSession(user);
        return user.getUuid();
    }

    /**
     * Case- and whitespace-fold so an allowlist match doesn't hinge on how the email was typed or how
     * Google returns it.
     */
    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
