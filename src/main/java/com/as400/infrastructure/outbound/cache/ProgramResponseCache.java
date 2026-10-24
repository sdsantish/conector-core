package com.as400.infrastructure.outbound.cache;

import com.as400.config.AS400Config;
import com.as400.core.domain.ProgramDefinition;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Caché local de respuestas, solo para los PCML configurados en as400.cache.&lt;pcml&gt;.
 * La clave es el programa más sus entradas ya validadas: dos peticiones comparten respuesta solo si envían
 * exactamente los mismos valores ("lima" y "LIMA" son claves distintas aunque el programa las trate igual).
 */
@ApplicationScoped
public class ProgramResponseCache {

    private final AS400Config config;
    private final MeterRegistry registry;
    private final Map<String, Cache<String, Map<String, Object>>> caches = new HashMap<>();

    public ProgramResponseCache(AS400Config config, MeterRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    @PostConstruct
    void start() {
        config.cache().forEach((pcml, settings) -> {
            Cache<String, Map<String, Object>> cache = Caffeine.newBuilder()
                    .expireAfterWrite(settings.ttl())
                    .maximumSize(settings.maxEntries())
                    .recordStats()
                    .build();
            CaffeineCacheMetrics.monitor(registry, cache, "as400-" + pcml, "pcml", pcml);
            caches.put(pcml, cache);
        });
    }

    /**
     * Devuelve la respuesta guardada o la obtiene con {@code call}. Las llamadas concurrentes con la misma
     * clave esperan a la primera en vez de ir todas al AS400. Los errores no se guardan.
     */
    public Map<String, Object> get(ProgramDefinition program, Map<String, Object> inputs,
                                   Supplier<Map<String, Object>> call) {
        Cache<String, Map<String, Object>> cache = caches.get(program.document());
        if (cache == null) {
            return call.get();
        }
        return cache.get(program.name() + "|" + inputs, key -> Collections.unmodifiableMap(call.get()));
    }
}
