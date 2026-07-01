package com.oppshan.washa.auth;

import com.oppshan.washa.exception.BusinessException;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Extracts the ID-token {@link JsonWebToken} from the {@link SecurityIdentity}. In the OIDC
 * web-app flow the principal is the ID token (the JWT carrying email/profile claims); under
 * {@code @TestSecurity}/{@code @JwtSecurity} it is the synthesized token. Reading from the
 * identity keeps the same code working in both, without an {@code @IdToken}-qualified injection.
 */
final class AuthSupport {

    /** Static-only helper; not instantiable. */
    private AuthSupport() {
    }

    /**
     * Pulls the {@link JsonWebToken} out of the identity's principal. The OIDC web-app flow and the
     * {@code @TestSecurity}/{@code @JwtSecurity} test path both set a JWT principal, so this succeeds
     * whenever the identity is actually authenticated.
     *
     * @throws BusinessException authentication-required (HTTP 401) when the principal isn't a JWT,
     *         i.e. there's no authenticated token to read
     */
    static JsonWebToken idToken(SecurityIdentity identity) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            return jwt;
        }

        throw BusinessException.authenticationRequired();
    }
}
