package com.oppshan.washa.user;

import com.oppshan.washa.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    private static final String PROVIDER = "google";

    private final IdpAccountRepository idpAccountRepository;
    private final UserAccountRepository userAccountRepository;
    private final AllowedIdentityRepository allowedIdentityRepository;

    @Inject
    public UserAccountService(IdpAccountRepository idpAccountRepository,
                              UserAccountRepository userAccountRepository,
                              AllowedIdentityRepository allowedIdentityRepository) {
        this.idpAccountRepository = idpAccountRepository;
        this.userAccountRepository = userAccountRepository;
        this.allowedIdentityRepository = allowedIdentityRepository;
    }

    @Transactional
    public UserAccountView resolveOrLink(JsonWebToken idToken) {
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
        idpAccountRepository.insertWithSession(account); // assigns the VERSION_7 uuid; FK to person
        return toView(account);
    }

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

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    // Google sends email_verified as a JSON boolean; some token representations use a string.
    private static boolean isEmailVerified(JsonWebToken idToken) {
        final Object claim = idToken.getClaim("email_verified");
        return Boolean.TRUE.equals(claim) || "true".equalsIgnoreCase(String.valueOf(claim));
    }
}
