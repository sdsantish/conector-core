package com.as400.infrastructure.outbound;

import com.as400.core.domain.ProgramDefinition;
import com.as400.core.ports.outbound.As400ProgramPort;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Reemplaza al adaptador de jt400 en los tests. Los decoradores reales (caché, resiliencia) se aplican igual.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeAs400ProgramPort implements As400ProgramPort {

    private final Map<String, Function<Map<String, Object>, Map<String, Object>>> behaviors = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> call(ProgramDefinition program, Map<String, Object> inputs) {
        calls.computeIfAbsent(program.document(), key -> new AtomicInteger()).incrementAndGet();
        return behaviors.getOrDefault(program.document(), received -> Map.of("recibido", received.toString()))
                .apply(inputs);
    }

    public void when(String pcml, Function<Map<String, Object>, Map<String, Object>> behavior) {
        behaviors.put(pcml, behavior);
    }

    public int calls(String pcml) {
        return calls.getOrDefault(pcml, new AtomicInteger()).get();
    }
}
