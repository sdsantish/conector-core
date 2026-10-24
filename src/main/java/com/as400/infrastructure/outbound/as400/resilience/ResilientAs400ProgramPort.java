package com.as400.infrastructure.outbound.as400.resilience;

import com.as400.core.domain.ProgramDefinition;
import com.as400.core.ports.outbound.As400ProgramPort;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Aplica bulkhead, circuit breaker y métricas a cualquier implementación de {@link As400ProgramPort}.
 * Va por dentro de la caché: una respuesta cacheada no ocupa cupo ni cuenta para el circuito.
 */
@Decorator
@Priority(20)
public abstract class ResilientAs400ProgramPort implements As400ProgramPort {

    @Inject
    @Delegate
    As400ProgramPort delegate;

    @Inject
    As400Resilience resilience;

    @Override
    public Map<String, Object> call(ProgramDefinition program, Map<String, Object> inputs) {
        return resilience.execute(program.document(), () -> delegate.call(program, inputs));
    }
}
