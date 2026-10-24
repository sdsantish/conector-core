package com.as400.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigMapping(prefix = "as400")
public interface AS400Config {

    String host();

    String user();

    String password();

    /**
     * Tiempo máximo de un programa. Al vencer, jt400 termina el trabajo en el AS400 y la llamada responde 504.
     */
    @WithDefault("60s")
    Duration callTimeout();

    /**
     * Tiempo máximo para abrir una conexión nueva con el AS400.
     */
    @WithDefault("10s")
    Duration connectTimeout();

    Pool pool();

    CircuitBreaker circuitBreaker();

    /**
     * Grupos de PCML con cupo propio de llamadas simultáneas (as400.bulkhead.&lt;grupo&gt;.*).
     */
    Map<String, BulkheadGroup> bulkhead();

    /**
     * PCML cuya respuesta se guarda en caché (as400.cache.&lt;pcml&gt;.*). Solo para programas de consulta.
     */
    Map<String, CacheSettings> cache();

    interface Pool {

        /**
         * Conexiones simultáneas máximas al AS400; también limita las llamadas concurrentes.
         */
        @WithDefault("10")
        int maxConnections();

        /**
         * Tiempo que una petición espera una conexión libre antes de responder 503.
         */
        @WithDefault("30s")
        Duration acquireTimeout();

        /**
         * Una conexión sin uso durante este tiempo se cierra.
         */
        @WithDefault("5m")
        Duration maxInactivity();

        /**
         * Una conexión se renueva al superar este tiempo de vida.
         */
        @WithDefault("1h")
        Duration maxLifetime();
    }

    /**
     * Circuit breaker por PCML. Solo cuentan como fallo los timeouts y los errores de conexión con el AS400,
     * no los errores de validación ni los del propio programa.
     */
    interface CircuitBreaker {

        @WithDefault("true")
        boolean enabled();

        /**
         * Llamadas que se evalúan para decidir si se abre el circuito.
         */
        @WithDefault("10")
        int requestVolumeThreshold();

        /**
         * Proporción de fallos (0-1) entre esas llamadas que abre el circuito.
         */
        @WithDefault("0.5")
        double failureRatio();

        /**
         * Tiempo que el circuito queda abierto antes de dejar pasar llamadas de prueba.
         */
        @WithDefault("30s")
        Duration delay();

        /**
         * Llamadas de prueba exitosas necesarias para volver a cerrarlo.
         */
        @WithDefault("2")
        int successThreshold();
    }

    interface BulkheadGroup {

        /**
         * Llamadas simultáneas máximas de los PCML del grupo (debe ser menor que pool.max-connections).
         */
        int maxConcurrent();

        /**
         * PCML del grupo (nombre del archivo sin .pcml).
         */
        List<String> pcmls();
    }

    interface CacheSettings {

        /**
         * Tiempo que una respuesta se considera válida.
         */
        Duration ttl();

        /**
         * Respuestas distintas (combinaciones de parámetros) que se guardan como máximo.
         */
        @WithDefault("1000")
        long maxEntries();
    }
}
