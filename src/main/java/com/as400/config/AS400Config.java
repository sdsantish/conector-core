package com.as400.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "as400")
public interface AS400Config {

    String host();

    String user();

    String password();

    Pool pool();

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
}
