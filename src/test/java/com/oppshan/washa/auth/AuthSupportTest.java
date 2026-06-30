package com.oppshan.washa.auth;

import com.oppshan.washa.exception.BusinessException;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** idToken rejects an identity whose principal is not a bearer JWT. */
class AuthSupportTest {

    @Test
    void shouldRejectAnIdentityWhosePrincipalIsNotAJwt() {
        final var identity = mock(SecurityIdentity.class);
        when(identity.getPrincipal()).thenReturn(mock(Principal.class));

        assertThrows(BusinessException.class, () -> AuthSupport.idToken(identity));
    }
}
