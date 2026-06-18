package com.oppshan.washa.user;

import com.oppshan.washa.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;

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

    /**
     * Resolves the authenticated Google identity to a person, linking it on first sight if the
     * verified email is on the allowlist. Throws {@link BusinessException#accessDenied()} for any
     * identity not already linked and not on the allowlist — washa is a closed two-user app.
     */
    @Transactional
    public UserAccountView resolveOrLink(JsonWebToken idToken) {
        String sub = idToken.getSubject();
        var existing = idpAccountRepository.findByProviderNameAndProviderId(PROVIDER, sub)
                .flatMap(IdpAccount::asGoogleAccount);
        if (existing.isPresent()) {
            return refreshAndView(existing.get(), idToken);
        }

        Boolean verified = idToken.getClaim("email_verified");
        String email = normalize(idToken.getClaim("email"));
        if (verified == null || !verified || email == null) {
            throw BusinessException.accessDenied();
        }

        AllowedIdentity allowed = allowedIdentityRepository.findByEmail(email)
                .orElseThrow(BusinessException::accessDenied);
        UserAccount person = userAccountRepository.findById(allowed.getUserAccountUuid())
                .orElseThrow(BusinessException::userNotFound);

        GoogleAccount account = new GoogleAccount()
                .setUserAccount(person)
                .setProviderName(PROVIDER)
                .setProviderId(sub)
                .setEmail(email)
                .setName(idToken.getClaim("name"))
                .setPhotoUrl(idToken.getClaim("picture"));
        person.getIdpAccounts().add(account);
        userAccountRepository.save(person);
        return toView(account);
    }

    private UserAccountView refreshAndView(GoogleAccount account, JsonWebToken idToken) {
        UserAccount person = account.getUserAccount();
        boolean changed = false;

        String givenName = idToken.getClaim("given_name");
        if (givenName != null && !givenName.equals(person.getFirstName())) {
            person.setFirstName(givenName);
            changed = true;
        }
        String familyName = idToken.getClaim("family_name");
        if (familyName != null && !familyName.equals(person.getLastName())) {
            person.setLastName(familyName);
            changed = true;
        }
        String name = idToken.getClaim("name");
        if (name != null && !name.equals(account.getName())) {
            account.setName(name);
            changed = true;
        }
        String picture = idToken.getClaim("picture");
        if (picture != null && !picture.equals(account.getPhotoUrl())) {
            account.setPhotoUrl(picture);
            changed = true;
        }
        if (changed) {
            userAccountRepository.save(person);
        }
        return toView(account);
    }

    private UserAccountView toView(GoogleAccount account) {
        UserAccount user = account.getUserAccount();
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        String display = (first + " " + last).trim();
        if (display.isEmpty()) {
            display = account.getName() != null ? account.getName() : account.getEmail();
        }
        return new UserAccountView(
                user.getUuid(), user.getFirstName(), user.getLastName(),
                display, account.getEmail(), account.getPhotoUrl());
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
