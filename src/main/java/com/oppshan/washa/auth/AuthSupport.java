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

    private AuthSupport() {
    }

    static JsonWebToken idToken(SecurityIdentity identity) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            return jwt;
        }

        throw BusinessException.authenticationRequired();
    }
}
