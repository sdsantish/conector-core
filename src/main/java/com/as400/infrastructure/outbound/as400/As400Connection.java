package com.as400.infrastructure.outbound.as400;

import com.as400.config.AS400Config;
import com.as400.core.domain.exception.As400UnavailableException;
import com.as400.core.domain.exception.ConnectorBusyException;
import com.as400.core.domain.exception.ProgramTimeoutException;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.ConnectionPoolException;
import com.ibm.as400.access.SocketProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Pool de conexiones al AS400. Reutiliza las conexiones entre llamadas y limita las llamadas simultáneas:
 * jt400 falla en vez de esperar al llegar al máximo, así que un semáforo hace esperar a las peticiones
 * hasta acquire-timeout. Las conexiones que quedan rotas (timeout, corte) se descartan en vez de devolverse.
 */
@Startup // Las métricas del pool existen desde el arranque; crear el pool no abre conexiones
@ApplicationScoped
public class As400Connection {

    private final AS400Config config;
    private final MeterRegistry registry;
    private AS400ConnectionPool pool;
    private Semaphore permits;

    public As400Connection(AS400Config config, MeterRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    @PostConstruct
    void start() {
        AS400Config.Pool settings = config.pool();
        SocketProperties socket = new SocketProperties();
        socket.setLoginTimeout((int) config.connectTimeout().toMillis());
        // Detecta conexiones muertas (AS400 reiniciado, firewall) mientras están en el pool
        socket.setKeepAlive(true);

        pool = new AS400ConnectionPool();
        pool.setSocketProperties(socket);
        pool.setMaxConnections(settings.maxConnections());
        pool.setMaxInactivity(settings.maxInactivity().toMillis());
        pool.setMaxLifetime(settings.maxLifetime().toMillis());
        permits = new Semaphore(settings.maxConnections(), true);

        Gauge.builder("as400.pool.in_use", this, As400Connection::inUse)
                .description("Conexiones al AS400 en uso").register(registry);
        Gauge.builder("as400.pool.max", this, As400Connection::maxConnections)
                .description("Conexiones al AS400 máximas").register(registry);
        Gauge.builder("as400.pool.waiting", permits, Semaphore::getQueueLength)
                .description("Peticiones esperando una conexión").register(registry);
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
        boolean broken = false;
        try {
            system = pool.getConnection(config.host(), config.user(), config.password().toCharArray(), AS400.COMMAND);
            return operation.apply(system);
        } catch (ConnectionPoolException e) {
            throw new As400UnavailableException("No se pudo conectar al AS400 " + config.host()
                    + ": " + e.getMessage(), e);
        } catch (ProgramTimeoutException | As400UnavailableException e) {
            // El trabajo se terminó o la conexión se cortó: no sirve para otra llamada
            broken = true;
            throw e;
        } finally {
            if (system != null) {
                if (broken) {
                    pool.removeFromPool(system);
                } else {
                    pool.returnConnectionToPool(system);
                }
            }
            permits.release();
        }
    }

    public int inUse() {
        return maxConnections() - permits.availablePermits();
    }

    public int maxConnections() {
        return config.pool().maxConnections();
    }

    private void acquire() {
        AS400Config.Pool settings = config.pool();
        try {
            if (!permits.tryAcquire(settings.acquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ConnectorBusyException("Las " + settings.maxConnections()
                        + " conexiones al AS400 están ocupadas; reintenta en unos segundos");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorBusyException("Se interrumpió la espera de una conexión al AS400");
        }
    }
}
