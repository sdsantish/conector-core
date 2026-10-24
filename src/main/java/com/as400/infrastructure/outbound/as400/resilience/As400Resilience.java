package com.as400.infrastructure.outbound.as400.resilience;

import com.as400.config.AS400Config;
import com.as400.core.domain.exception.As400UnavailableException;
import com.as400.core.domain.exception.ConnectorBusyException;
import com.as400.core.domain.exception.ProgramExecutionException;
import com.as400.core.domain.exception.ProgramTimeoutException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.Guard;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Protecciones alrededor de cada llamada al AS400:
 * <ul>
 *   <li><b>Bulkhead</b>: los PCML de un grupo comparten un cupo de llamadas simultáneas, para que un programa
 *       lento no ocupe todas las conexiones del pool.</li>
 *   <li><b>Circuit breaker por PCML</b>: si un programa acumula timeouts o errores de conexión, sus llamadas
 *       fallan de inmediato durante un tiempo en vez de seguir cargando al AS400. Los errores de validación y
 *       los del propio programa no cuentan.</li>
 *   <li><b>Métricas</b>: duración y resultado de cada llamada por PCML, estado de circuitos y grupos.</li>
 * </ul>
 */
@ApplicationScoped
public class As400Resilience {

    private static final Logger LOG = Logger.getLogger(As400Resilience.class);
    private static final TypeLiteral<Map<String, Object>> RESULT = new TypeLiteral<>() {
    };

    private record BulkheadGroup(String name, int maxConcurrent, Semaphore permits) {
    }

    private final AS400Config config;
    private final MeterRegistry registry;
    private final Map<String, Guard> guards = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerState> circuits = new ConcurrentHashMap<>();
    private final Map<String, BulkheadGroup> groupsByPcml = new HashMap<>();

    public As400Resilience(AS400Config config, MeterRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    @PostConstruct
    void start() {
        int poolSize = config.pool().maxConnections();
        config.bulkhead().forEach((name, settings) -> {
            if (settings.maxConcurrent() >= poolSize) {
                LOG.warnf("El grupo %s permite %d llamadas y el pool tiene %d conexiones: no protege a los demás",
                        name, settings.maxConcurrent(), poolSize);
            }
            BulkheadGroup group = new BulkheadGroup(name, settings.maxConcurrent(),
                    new Semaphore(settings.maxConcurrent(), true));
            for (String pcml : settings.pcmls()) {
                BulkheadGroup previous = groupsByPcml.put(pcml, group);
                if (previous != null) {
                    throw new IllegalStateException("El PCML " + pcml + " está en los grupos "
                            + previous.name() + " y " + name + " de as400.bulkhead");
                }
            }
            Gauge.builder("as400.bulkhead.in_use", group,
                            g -> g.maxConcurrent() - g.permits().availablePermits())
                    .tag("group", name)
                    .description("Llamadas en curso del grupo").register(registry);
        });
    }

    public Map<String, Object> execute(String pcml, Supplier<Map<String, Object>> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            BulkheadGroup group = groupsByPcml.get(pcml);
            if (group == null) {
                return guarded(pcml, call);
            }
            acquire(group);
            try {
                return guarded(pcml, call);
            } finally {
                group.permits().release();
            }
        } catch (RuntimeException e) {
            outcome = outcome(e);
            throw e;
        } finally {
            sample.stop(Timer.builder("as400.program.calls")
                    .description("Llamadas a programas del AS400")
                    .tag("pcml", pcml)
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }

    /**
     * PCML con el circuito abierto o en prueba (half-open).
     */
    public Map<String, String> openCircuits() {
        Map<String, String> open = new HashMap<>();
        circuits.forEach((pcml, state) -> {
            if (state != CircuitBreakerState.CLOSED) {
                open.put(pcml, state.name());
            }
        });
        return open;
    }

    private Map<String, Object> guarded(String pcml, Supplier<Map<String, Object>> call) {
        if (!config.circuitBreaker().enabled()) {
            return call.get();
        }
        try {
            return guards.computeIfAbsent(pcml, this::createGuard).get(call, RESULT);
        } catch (CircuitBreakerOpenException e) {
            throw new As400UnavailableException("Las últimas llamadas a " + pcml + " fallaron por timeout o "
                    + "conexión; se pausan durante " + config.circuitBreaker().delay().toSeconds()
                    + "s para no sobrecargar el AS400", e);
        }
    }

    private Guard createGuard(String pcml) {
        AS400Config.CircuitBreaker settings = config.circuitBreaker();
        circuits.put(pcml, CircuitBreakerState.CLOSED);
        Gauge.builder("as400.circuit.state", circuits, states -> stateValue(states.get(pcml)))
                .tag("pcml", pcml)
                .description("Estado del circuito: 0 cerrado, 1 en prueba, 2 abierto").register(registry);

        return Guard.create()
                .withDescription("as400-" + pcml)
                .withCircuitBreaker()
                .name("as400-" + pcml)
                .requestVolumeThreshold(settings.requestVolumeThreshold())
                .failureRatio(settings.failureRatio())
                .delay(settings.delay().toMillis(), ChronoUnit.MILLIS)
                .successThreshold(settings.successThreshold())
                // Solo los problemas del AS400 abren el circuito, no los de la petición o del programa
                .when(e -> e instanceof ProgramTimeoutException || e instanceof As400UnavailableException)
                .onStateChange(state -> {
                    circuits.put(pcml, state);
                    LOG.warnf("Circuito de %s: %s", pcml, state);
                })
                .done()
                .build();
    }

    private void acquire(BulkheadGroup group) {
        try {
            if (!group.permits().tryAcquire(config.pool().acquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ConnectorBusyException("El grupo " + group.name() + " ya tiene " + group.maxConcurrent()
                        + " llamadas en curso; reintenta en unos segundos");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorBusyException("Se interrumpió la espera del grupo " + group.name());
        }
    }

    private static String outcome(RuntimeException e) {
        if (e instanceof ProgramExecutionException) {
            return "program_error";
        }
        if (e instanceof ProgramTimeoutException) {
            return "timeout";
        }
        if (e instanceof As400UnavailableException) {
            return e.getCause() instanceof CircuitBreakerOpenException ? "circuit_open" : "unavailable";
        }
        if (e instanceof ConnectorBusyException) {
            return "busy";
        }
        return "error";
    }

    private static double stateValue(CircuitBreakerState state) {
        return switch (state == null ? CircuitBreakerState.CLOSED : state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
