package com.as400.infrastructure.outbound;

import com.as400.core.domain.exception.As400UnavailableException;
import com.as400.core.domain.exception.ProgramExecutionException;
import com.as400.core.domain.exception.ProgramTimeoutException;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuración en application.properties (%test): caché para saludo, grupo "lentos" de 1 llamada con suma,
 * circuito que se evalúa cada 4 llamadas y queda abierto 1h. Cada test usa un PCML distinto para no
 * compartir circuito.
 */
@QuarkusTest
class ResilienceAndCacheTest {

    @Inject
    FakeAs400ProgramPort as400;

    @Test
    void cachesResponsesPerInput() {
        post("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"ANA\"}}").statusCode(200);
        post("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"ANA\"}}").statusCode(200);
        assertEquals(1, as400.calls("saludo"));

        post("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"LUIS\"}}").statusCode(200);
        assertEquals(2, as400.calls("saludo"));
    }

    @Test
    void opensCircuitAfterConnectionFailures() {
        as400.when("cliins", inputs -> {
            throw new As400UnavailableException("AS400 caído");
        });
        String body = "{\"pcml\": \"cliins\", \"parametros\": {\"nombre\": \"ANA\", \"ciudad\": \"LIMA\"}}";

        for (int i = 0; i < 4; i++) {
            post(body).statusCode(503).body("error", is("AS400 caído"));
        }
        post(body).statusCode(503).body("error", containsString("se pausan"));
        assertEquals(4, as400.calls("cliins"));

        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("checks.find { it.name == 'as400' }.data.'circuit.cliins'", is("OPEN"));
    }

    @Test
    void programErrorsDoNotOpenCircuit() {
        as400.when("clicon", inputs -> {
            throw new ProgramExecutionException("El programa clicon falló: CPF0001 - error");
        });
        for (int i = 0; i < 6; i++) {
            post("{\"pcml\": \"clicon\", \"parametros\": {\"ciudad\": \"LIMA\"}}").statusCode(502);
        }
        assertEquals(6, as400.calls("clicon"));
    }

    @Test
    void timeoutIsGatewayTimeout() {
        as400.when("pruebapcml", inputs -> {
            throw new ProgramTimeoutException("El programa PRUEBAPCML superó el tiempo máximo", null);
        });
        post("{\"pcml\": \"pruebapcml\", \"parametros\": {}}")
                .statusCode(504)
                .body("error", containsString("superó el tiempo máximo"));
    }

    @Test
    void bulkheadLimitsItsGroup() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        as400.when("suma", inputs -> {
            inside.countDown();
            await(release);
            return Map.of("result", 3);
        });
        String body = "{\"pcml\": \"suma\", \"parametros\": {\"num1\": 1, \"num2\": 2}}";

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> post(body).extract().statusCode());
        assertTrue(inside.await(10, TimeUnit.SECONDS));

        post(body).statusCode(503).body("error", is("El grupo lentos ya tiene 1 llamadas en curso; "
                + "reintenta en unos segundos"));
        // Otros PCML no comparten el cupo del grupo
        post("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"OTRO\"}}").statusCode(200);

        release.countDown();
        assertEquals(200, first.get(10, TimeUnit.SECONDS));
    }

    @Test
    void exposesMetrics() {
        post("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"METRICAS\"}}").statusCode(200);

        given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("as400_program_calls_seconds_count{outcome=\"success\",pcml=\"saludo\"}"))
                .body(containsString("as400_pool_max"))
                .body(containsString("cache_gets_total{cache=\"as400-saludo\""));
    }

    private static ValidatableResponse post(String body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/programa")
                .then();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
