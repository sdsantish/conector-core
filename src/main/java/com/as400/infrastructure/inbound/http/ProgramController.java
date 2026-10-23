package com.as400.infrastructure.inbound.http;

import com.as400.core.domain.ProgramRequest;
import com.as400.core.domain.ProgramResult;
import com.as400.core.domain.exception.InvalidProgramRequestException;
import com.as400.core.ports.inbound.ExecuteProgramUseCase;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoint genérico: ejecuta cualquier programa del AS400 que tenga su PCML.
 * Los errores se traducen a HTTP en {@link HttpExceptionMapper}.
 */
@Path("/programa")
public class ProgramController {

    private final ExecuteProgramUseCase executeProgram;

    public ProgramController(ExecuteProgramUseCase executeProgram) {
        this.executeProgram = executeProgram;
    }

    @POST
    @Blocking
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProgramResponseDto execute(ProgramRequestDto request) {
        if (request == null) {
            throw new InvalidProgramRequestException("El body es obligatorio");
        }
        ProgramResult result = executeProgram.execute(
                new ProgramRequest(request.pcml(), request.programa(), request.parametros()));
        return new ProgramResponseDto(result.document(), result.program(), result.outputs());
    }
}
