package com.oppshan.washa.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void shouldCarryStatusAndCodeForEachFactory() {
        assertThat(BusinessException.authenticationRequired())
                .hasFieldOrPropertyWithValue("status", 401)
                .hasFieldOrPropertyWithValue("messageCode", MessageCode.AUTHENTICATION_REQUIRED);
        assertThat(BusinessException.accessDenied())
                .hasFieldOrPropertyWithValue("status", 403)
                .hasFieldOrPropertyWithValue("messageCode", MessageCode.ACCESS_DENIED);
        assertThat(BusinessException.userNotFound())
                .hasFieldOrPropertyWithValue("status", 404)
                .hasFieldOrPropertyWithValue("messageCode", MessageCode.USER_NOT_FOUND);
    }

    @Test
    void shouldUseTheMessageCodeKeyAsTheExceptionMessage() {
        assertThat(BusinessException.accessDenied().getMessage())
                .isEqualTo(MessageCode.ACCESS_DENIED.getKey());
    }

    @Test
    void shouldRenderStatusAndCodeNameViaMapper() {
        try (final Response response = new BusinessExceptionMapper().toResponse(BusinessException.userNotFound())) {
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getEntity())
                    .isEqualTo(new BusinessExceptionMapper.ErrorBody("USER_NOT_FOUND"));
        }
    }
}
