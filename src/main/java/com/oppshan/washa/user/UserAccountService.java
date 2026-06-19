package com.oppshan.washa.user;

import com.oppshan.washa.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Resolves an authenticated Google identity to a household person, linking it on first sight if
 * the verified email is on the allowlist. washa is a closed two-user app, so any identity that is
 * neither already linked nor allowlisted is denied.
 *
 * <p>Mutations use the stateful {@link EntityManager} (managed entities, generator + cascade);
 * the Jakarta Data repositories are used only for read-only lookups.
 */
@ApplicationScoped
public class UserAccountService {

    private static final String PROVIDER = "google";

    private final IdpAccountRepository idpAccountRepository;
    private final AllowedIdentityRepository allowedIdentityRepository;
    private final EntityManager entityManager;

    @Inject
    public UserAccountService(IdpAccountRepository idpAccountRepository,
                              AllowedIdentityRepository allowedIdentityRepository,
                              EntityManager entityManager) {
        this.idpAccountRepository = idpAccountRepository;
        this.allowedIdentityRepository = allowedIdentityRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public UserAccountView resolveOrLink(JsonWebToken idToken) {
        final var sub = idToken.getSubject();
        final var existing = idpAccountRepository.findByProviderNameAndProviderId(PROVIDER, sub)
                .flatMap(IdpAccount::asGoogleAccount);
        if (existing.isPresent()) {
            // Re-load as a managed entity so refresh edits flush on commit.
            final var managed = entityManager.find(GoogleAccount.class, existing.get().getUuid());
            return refreshAndView(managed, idToken);
        }

        final var email = normalize(idToken.getClaim("email"));
        if (!isEmailVerified(idToken) || email == null) {
            throw BusinessException.accessDenied();
        }

        final var allowed = allowedIdentityRepository.findByEmail(email)
                .orElseThrow(BusinessException::accessDenied);
        // The allowed_identity -> user_account FK guarantees the person exists; a managed
        // reference is enough to attach the new Google account.
        final var person = entityManager.getReference(UserAccount.class, allowed.getUserAccountUuid());

        final var account = new GoogleAccount()
                .setUserAccount(person)
                .setProviderName(PROVIDER)
                .setProviderId(sub)
                .setEmail(email)
                .setName(idToken.getClaim("name"))
                .setPhotoUrl(idToken.getClaim("picture"));
        person.getIdpAccounts().add(account);
        entityManager.persist(account); // assigns the VERSION_7 uuid; FK to the managed person
        return toView(account);
    }

    private UserAccountView refreshAndView(GoogleAccount account, JsonWebToken idToken) {
        final var person = account.getUserAccount();

        final String givenName = idToken.getClaim("given_name");
        if (givenName != null && !givenName.equals(person.getFirstName())) {
            person.setFirstName(givenName);
        }
        final String familyName = idToken.getClaim("family_name");
        if (familyName != null && !familyName.equals(person.getLastName())) {
            person.setLastName(familyName);
        }
        final String name = idToken.getClaim("name");
        if (name != null && !name.equals(account.getName())) {
            account.setName(name);
        }
        final String picture = idToken.getClaim("picture");
        if (picture != null && !picture.equals(account.getPhotoUrl())) {
            account.setPhotoUrl(picture);
        }
        // account/person are managed — dirty changes flush at commit.
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
