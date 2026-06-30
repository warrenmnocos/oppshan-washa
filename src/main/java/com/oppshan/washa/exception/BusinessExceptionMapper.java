package com.oppshan.washa.exception;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException exception) {
        return Response.status(exception.getStatus())
                .entity(new ErrorBody(exception.getMessageCode().getKey()))
                .build();
    }

    /**
     * The JSON error body returned for a failed request — the {@link MessageCode} key the frontend
     * resolves to a localized error message.
     */
    @RegisterForReflection
    public record ErrorBody(String messageCode) {
    }
}
