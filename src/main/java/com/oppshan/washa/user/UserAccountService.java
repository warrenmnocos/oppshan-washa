package com.oppshan.washa.user;

import com.oppshan.washa.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Resolves an authenticated Google identity to a household person, linking it on first sight if
 * the verified email is on the allowlist. washa is a closed two-user app, so any identity that is
 * neither already linked nor allowlisted is denied. A person's name comes from the allowlist seed
 * (Parameter Store), so the login does not overwrite it from the token.
 *
 * <p>All persistence goes through the repositories (their {@code StatefulWriteRepository} mixin
 * supplies managed-session writes); the service never touches an {@code EntityManager}.
 */
@ApplicationScoped
public class UserAccountService {

    /** The only IdP wired up; stored as each {@code IdpAccount.providerName}. */
    private static final String PROVIDER = "google";

    private final IdpAccountRepository idpAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final AllowedIdentityRepository allowedIdentityRepository;

    /** Injects the three repositories this service reads the allowlist and identities through. */
    @Inject
    public UserAccountService(IdpAccountRepository idpAccountRepository,
                              UserAccountRepository userAccountRepository,
                              AllowedIdentityRepository allowedIdentityRepository) {
        this.idpAccountRepository = idpAccountRepository;
        this.userAccountRepository = userAccountRepository;
        this.allowedIdentityRepository = allowedIdentityRepository;
    }

    /**
     * Maps a verified Google ID token to its household person, creating the link on first sight. A
     * returning identity is matched by ({@code google}, {@code sub}) and its view returned straight
     * away. A brand-new identity must clear the allowlist gate: the token's email has to be verified
     * and present, on the allowlist, and resolve to a seeded person; only then is a
     * {@code GoogleAccount} created (capturing the {@code sub} and profile fields) and persisted
     * through {@code insertWithSession}, a managed persist that assigns the VERSION_7 {@code uuid}
     * and writes the FK to the person. The person's name is left as seeded, never overwritten from
     * the token.
     *
     * @throws BusinessException access-denied (403) when the email is unverified or missing, not on
     *         the allowlist, or its person can't be resolved
     */
    @Transactional
    @Valid
    @NotNull
    public UserAccountView resolveOrLink(@NotNull JsonWebToken idToken) {
        final var existing = idpAccountRepository.findGoogleByProvider(PROVIDER, idToken.getSubject());
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        final var email = normalize(idToken.getClaim("email"));
        if (!isEmailVerified(idToken) || email == null) {
            throw BusinessException.accessDenied();
        }

        final var allowed = allowedIdentityRepository.findByEmail(email)
                .orElseThrow(BusinessException::accessDenied);
        final var person = userAccountRepository.findById(allowed.getUserAccountUuid())
                .orElseThrow(BusinessException::accessDenied);
        final var account = new GoogleAccount()
                .setUserAccount(person)
                .setProviderName(PROVIDER)
                .setProviderId(idToken.getSubject())
                .setEmail(email)
                .setName(idToken.getClaim("name"))
                .setPhotoUrl(idToken.getClaim("picture"));
        idpAccountRepository.insertWithSession(account);
        return toView(account);
    }

    /**
     * Builds the {@link UserAccountView} for a linked Google account. {@code displayName} prefers
     * the person's seeded "first last" (trimmed); when that's blank it falls back to the Google
     * account's own name, then to the email, so {@code displayName} is never empty even before
     * names are seeded.
     */
    private UserAccountView toView(GoogleAccount account) {
        final var user = account.getUserAccount();
        final var first = user.getFirstName() == null ? "" : user.getFirstName();
        final var last = user.getLastName() == null ? "" : user.getLastName();
        final var trimmed = (first + " " + last).trim();
        final String displayName;
        if (!trimmed.isEmpty()) {
            displayName = trimmed;
        } else if (account.getName() != null) {
            displayName = account.getName();
        } else {
            displayName = account.getEmail();
        }

        return new UserAccountView(
                user.getUuid(), user.getFirstName(), user.getLastName(),
                displayName, account.getEmail(), account.getPhotoUrl());
    }

    /**
     * Lower-cases and trims an email so a lookup matches the normalized addresses stored in the
     * allowlist. Returns null for null input.
     */
    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Reads the {@code email_verified} claim, which Google sends as a JSON boolean but some token
     * representations render as the string {@code "true"}; accepts either form.
     */
    private static boolean isEmailVerified(JsonWebToken idToken) {
        final Object claim = idToken.getClaim("email_verified");
        return Boolean.TRUE.equals(claim) || "true".equalsIgnoreCase(String.valueOf(claim));
    }
}
