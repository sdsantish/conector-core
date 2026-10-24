package com.as400.infrastructure.outbound.cache;

import com.as400.core.domain.ProgramDefinition;
import com.as400.core.ports.outbound.As400ProgramPort;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Responde desde la caché a los PCML configurados en as400.cache sin llegar al AS400.
 * Es el decorador más externo: un acierto de caché no pasa por el bulkhead ni por el circuit breaker.
 */
@Decorator
@Priority(10)
public abstract class CachedAs400ProgramPort implements As400ProgramPort {

    @Inject
    @Delegate
    As400ProgramPort delegate;

    @Inject
    ProgramResponseCache cache;

    @Override
    public Map<String, Object> call(ProgramDefinition program, Map<String, Object> inputs) {
        return cache.get(program, inputs, () -> delegate.call(program, inputs));
    }
}
