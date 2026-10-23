package com.as400.infrastructure.outbound.as400;

import com.as400.config.AS400Config;
import com.as400.core.domain.exception.As400UnavailableException;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.ConnectionPoolException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Pool de conexiones al AS400. Reutiliza las conexiones entre llamadas y limita las llamadas simultáneas:
 * jt400 falla en vez de esperar al llegar al máximo, así que un semáforo hace esperar a las peticiones
 * hasta acquire-timeout.
 */
@ApplicationScoped
public class As400Connection {

    private final AS400Config config;
    private AS400ConnectionPool pool;
    private Semaphore permits;

    public As400Connection(AS400Config config) {
        this.config = config;
    }

    @PostConstruct
    void start() {
        AS400Config.Pool settings = config.pool();
        pool = new AS400ConnectionPool();
        pool.setMaxConnections(settings.maxConnections());
        pool.setMaxInactivity(settings.maxInactivity().toMillis());
        pool.setMaxLifetime(settings.maxLifetime().toMillis());
        permits = new Semaphore(settings.maxConnections(), true);
    }

    @PreDestroy
    void stop() {
        pool.close();
    }

    /**
     * Ejecuta la operación con una conexión del pool y la devuelve al terminar.
     */
    public <T> T execute(Function<AS400, T> operation) {
        acquire();
        AS400 system = null;
        try {
            system = pool.getConnection(config.host(), config.user(), config.password().toCharArray(), AS400.COMMAND);
            return operation.apply(system);
        } catch (ConnectionPoolException e) {
            throw new As400UnavailableException("No se pudo conectar al AS400 " + config.host()
                    + ": " + e.getMessage(), e);
        } finally {
            if (system != null) {
                pool.returnConnectionToPool(system);
            }
            permits.release();
        }
    }

    private void acquire() {
        AS400Config.Pool settings = config.pool();
        try {
            if (!permits.tryAcquire(settings.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new As400UnavailableException("Las " + settings.maxConnections()
                        + " conexiones al AS400 están ocupadas; reintenta en unos segundos");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new As400UnavailableException("Se interrumpió la espera de una conexión al AS400", e);
        }
    }
}
