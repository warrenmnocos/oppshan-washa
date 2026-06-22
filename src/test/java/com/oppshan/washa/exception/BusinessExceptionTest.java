package com.oppshan.washa.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class BusinessExceptionTest {

    @Test
    void shouldCarryStatusAndCodeForEachFactory() {
        final var authRequired = BusinessException.authenticationRequired();
        assertThat(authRequired.getStatus(), is(401));
        assertThat(authRequired.getMessageCode(), is(MessageCode.AUTHENTICATION_REQUIRED));

        final var accessDenied = BusinessException.accessDenied();
        assertThat(accessDenied.getStatus(), is(403));
        assertThat(accessDenied.getMessageCode(), is(MessageCode.ACCESS_DENIED));

        final var userNotFound = BusinessException.userNotFound();
        assertThat(userNotFound.getStatus(), is(404));
        assertThat(userNotFound.getMessageCode(), is(MessageCode.USER_NOT_FOUND));
    }

    @Test
    void shouldUseTheMessageCodeKeyAsTheExceptionMessage() {
        assertThat(BusinessException.accessDenied().getMessage(), is(MessageCode.ACCESS_DENIED.getKey()));
    }

    @Test
    void shouldRenderStatusAndCodeNameViaMapper() {
        try (final Response response = new BusinessExceptionMapper().toResponse(BusinessException.userNotFound())) {
            assertThat(response.getStatus(), is(404));
            assertThat(response.getEntity(), is(new BusinessExceptionMapper.ErrorBody("USER_NOT_FOUND")));
        }
    }
}
