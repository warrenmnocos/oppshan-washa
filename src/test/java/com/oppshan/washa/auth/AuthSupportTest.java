package com.oppshan.washa.auth;

import com.oppshan.washa.exception.BusinessException;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

/** idToken rejects an identity whose principal is not a bearer JWT. */
@ExtendWith(MockitoExtension.class)
class AuthSupportTest {

    @Mock
    SecurityIdentity identity;

    @Mock
    Principal principal;

    @Test
    void shouldRejectAnIdentityWhosePrincipalIsNotAJwt() {
        given(identity.getPrincipal()).willReturn(principal);

        assertThrows(BusinessException.class, () -> AuthSupport.idToken(identity));
    }
}
