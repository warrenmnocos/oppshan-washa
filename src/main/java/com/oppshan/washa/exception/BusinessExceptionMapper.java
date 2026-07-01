package com.oppshan.washa.exception;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * The single JAX-RS mapper for {@link BusinessException}: renders it as the HTTP status the exception
 * carries plus a JSON {@link ErrorBody} holding the {@link MessageCode} key. Because the status comes
 * from the exception, this one mapper serves 400 / 401 / 403 / 404 alike.
 */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    /** Renders the exception as its carried HTTP status plus an {@link ErrorBody} of the {@link MessageCode} key. */
    @Override
    public Response toResponse(BusinessException exception) {
        return Response.status(exception.getStatus())
                .entity(new ErrorBody(exception.getMessageCode().getKey()))
                .build();
    }

    /**
     * The JSON error body: just the {@link MessageCode} key (e.g. {@code "messages.errors.userNotFound"}).
     * Marked {@code @RegisterForReflection} because the native image reaches this record only through
     * Jackson serialization, never a typed handler, so it wouldn't otherwise be registered (backend
     * CLAUDE.md A.11).
     */
    @RegisterForReflection
    public record ErrorBody(String messageCode) {
    }
}
