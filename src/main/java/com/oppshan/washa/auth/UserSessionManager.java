package com.oppshan.washa.auth;

import com.oppshan.washa.user.UserAccountView;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Abstraction over the authenticated session, decoupling endpoints from the OIDC plumbing (mirrors
 * oppshan-files). The single implementation, {@link OidcUserSessionManager}, is stateless and
 * cookie-backed: washa runs as a Lambda behind CloudFront with no sticky sessions, so there is no
 * server-side (servlet) session to cache into — the OIDC session lives in the encrypted
 * {@code q_session} cookie and the household person is re-derived per request. (This is why
 * oppshan-files' {@code SessionScopedUserSessionManager}, which caches in an {@code HttpSession},
 * is deliberately not ported.)
 */
public interface UserSessionManager extends Serializable {

    boolean isSignedOut();

    @NotNull
    UserAccountView sessionUserAccount();

    void signOut();
}
