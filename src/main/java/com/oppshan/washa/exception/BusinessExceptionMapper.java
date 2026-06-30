package com.oppshan.washa.exception;

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

    public record ErrorBody(String messageCode) {
    }
}
