package com.as400.usecases;

import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.ProgramInputValidator;
import com.as400.core.domain.ProgramRequest;
import com.as400.core.domain.ProgramResult;
import com.as400.core.ports.inbound.ExecuteProgramUseCase;
import com.as400.core.ports.outbound.As400ProgramPort;
import com.as400.core.ports.outbound.ProgramDefinitionPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Busca la definición del programa, valida las entradas y lo ejecuta en el AS400.
 */
@ApplicationScoped
public class ExecuteProgramService implements ExecuteProgramUseCase {

    private final ProgramDefinitionPort definitions;
    private final As400ProgramPort as400;

    public ExecuteProgramService(ProgramDefinitionPort definitions, As400ProgramPort as400) {
        this.definitions = definitions;
        this.as400 = as400;
    }

    @Override
    public ProgramResult execute(ProgramRequest request) {
        ProgramDefinition program = definitions.find(request.document(), request.program());
        // Se valida antes de pedir una conexión: una petición inválida no ocupa el AS400
        Map<String, Object> inputs = ProgramInputValidator.validate(program, request.parameters());
        Map<String, Object> outputs = as400.call(program, inputs);
        return new ProgramResult(program.document(), program.name(), outputs);
    }
}
