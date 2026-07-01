package com.oppshan.washa.auth;

import com.oppshan.washa.user.UserAccountView;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * Abstraction over the authenticated session that hides the OIDC plumbing behind a stable contract
 * (mirrors oppshan-files). The single implementation, {@link OidcUserSessionManager}, is stateless and
 * cookie-backed: washa runs as a Lambda behind CloudFront with no sticky sessions, so there is no
 * server-side (servlet) session to cache into — the OIDC session lives in the encrypted
 * {@code q_session} cookie and the household person is re-derived per request. (This is why
 * oppshan-files' {@code SessionScopedUserSessionManager}, which caches in an {@code HttpSession},
 * is deliberately not ported.)
 */
public interface UserSessionManager extends Serializable {

    /** Whether the current request has no authenticated session (an anonymous caller). */
    boolean isSignedOut();

    /**
     * The household person for the current authenticated request. Resolves the identity to a person,
     * linking it on first sight when the verified email is on the two-user allowlist. Never null: an
     * identity that can't be resolved to an allowlisted person throws {@code BusinessException} rather
     * than returning null.
     */
    @NotNull
    UserAccountView sessionUserAccount();

    /**
     * Ends the current session with a local logout (clears the session cookie). Safe to call when
     * already signed out.
     */
    void signOut();
}
