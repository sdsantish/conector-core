package com.as400.infrastructure.inbound.http;

import com.as400.core.domain.exception.As400UnavailableException;
import com.as400.core.domain.exception.ConnectorBusyException;
import com.as400.core.domain.exception.InvalidProgramRequestException;
import com.as400.core.domain.exception.ProgramDefinitionException;
import com.as400.core.domain.exception.ProgramExecutionException;
import com.as400.core.domain.exception.ProgramTimeoutException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduce las excepciones del dominio a respuestas HTTP con cuerpo { "error": "..." }.
 */
public class HttpExceptionMapper {

    private static final Logger LOG = Logger.getLogger(HttpExceptionMapper.class);

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> invalidRequest(InvalidProgramRequestException e) {
        return error(Response.Status.BAD_REQUEST, e);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> executionFailed(ProgramExecutionException e) {
        LOG.warn(e.getMessage(), e);
        return error(Response.Status.BAD_GATEWAY, e);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> unavailable(As400UnavailableException e) {
        // Sin stack trace: con el circuito abierto se repite en cada petición
        LOG.warn(e.getMessage());
        return error(Response.Status.SERVICE_UNAVAILABLE, e);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> busy(ConnectorBusyException e) {
        LOG.warn(e.getMessage());
        return error(Response.Status.SERVICE_UNAVAILABLE, e);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> timeout(ProgramTimeoutException e) {
        LOG.warn(e.getMessage());
        return error(Response.Status.GATEWAY_TIMEOUT, e);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponseDto> invalidDefinition(ProgramDefinitionException e) {
        LOG.error(e.getMessage(), e);
        return error(Response.Status.INTERNAL_SERVER_ERROR, e);
    }

    private static RestResponse<ErrorResponseDto> error(Response.Status status, RuntimeException e) {
        return RestResponse.status(status, new ErrorResponseDto(e.getMessage()));
    }
}
