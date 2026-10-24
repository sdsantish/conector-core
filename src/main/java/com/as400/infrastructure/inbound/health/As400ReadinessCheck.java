package com.as400.infrastructure.inbound.health;

import com.as400.infrastructure.outbound.as400.As400Connection;
import com.as400.infrastructure.outbound.as400.resilience.As400Resilience;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Estado del conector en /q/health/ready.
 * Siempre responde UP: si el AS400 cae, sacar todas las instancias del balanceador no ayuda (las peticiones
 * fallarían igual) y el circuit breaker ya responde rápido. El detalle sirve para monitorizar.
 */
@Readiness
@ApplicationScoped
public class As400ReadinessCheck implements HealthCheck {

    private final As400Connection connection;
    private final As400Resilience resilience;

    public As400ReadinessCheck(As400Connection connection, As400Resilience resilience) {
        this.connection = connection;
        this.resilience = resilience;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("as400")
                .up()
                .withData("pool.inUse", connection.inUse())
                .withData("pool.max", connection.maxConnections());
        resilience.openCircuits().forEach((pcml, state) -> response.withData("circuit." + pcml, state));
        return response.build();
    }
}
