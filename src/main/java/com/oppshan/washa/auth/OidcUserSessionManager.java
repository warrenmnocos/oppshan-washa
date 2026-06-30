package com.oppshan.washa.auth;

import com.oppshan.washa.user.UserAccountService;
import com.oppshan.washa.user.UserAccountView;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

/**
 * OIDC-backed {@link UserSessionManager}. Stateless: the signed-in state is read from the
 * {@link SecurityIdentity} (which Quarkus OIDC decrypts from the {@code q_session} cookie on each
 * request), and the household person is resolved/linked against the allowlist through
 * {@link UserAccountService}. Sign-out is a local logout — Google advertises no
 * {@code end_session_endpoint}, so RP-Initiated Logout is unavailable; {@link OidcSession#logout()}
 * clears the session cookie.
 *
 * <p>{@link OidcSession} is injected as an {@link Instance} on purpose: the test profile disables
 * OIDC ({@code %test.quarkus.oidc.enabled=false} — a web-app tenant left enabled hangs
 * {@code @TestSecurity} requests), which removes the bean. The {@code Instance} stays injectable and
 * resolves to the real session only when OIDC is active (dev/prod), so this manager is constructible
 * under test while still performing a real logout in production.
 *
 * <p>It is {@code @RequestScoped} (not {@code @ApplicationScoped} as in oppshan-files) so the injected
 * {@link SecurityIdentity} resolves within the active request: an application-scoped proxy can resolve
 * an identity whose JWT claims are not populated under {@code @TestSecurity}, which silently breaks the
 * allowlist lookup.
 */
@RequestScoped
public class OidcUserSessionManager implements UserSessionManager {

    private final SecurityIdentity securityIdentity;
    private final Instance<OidcSession> oidcSession;
    private final UserAccountService userAccountService;

    @Inject
    public OidcUserSessionManager(SecurityIdentity securityIdentity,
                                  Instance<OidcSession> oidcSession,
                                  UserAccountService userAccountService) {
        this.securityIdentity = securityIdentity;
        this.oidcSession = oidcSession;
        this.userAccountService = userAccountService;
    }

    @Override
    public boolean isSignedOut() {
        return securityIdentity.isAnonymous();
    }

    @NotNull
    @Override
    public UserAccountView sessionUserAccount() {
        return userAccountService.resolveOrLink(AuthSupport.idToken(securityIdentity));
    }

    @Override
    public void signOut() {
        if (oidcSession.isResolvable()) {
            oidcSession.get()
                    .logout()
                    .await()
                    .indefinitely();
        }
    }
}
