package com.as400.infrastructure.inbound.http;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Casos que se resuelven antes de llamar al AS400.
 */
@QuarkusTest
class ProgramControllerTest {

    @Test
    void unknownPcmlIsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"pcml\": \"noexiste\", \"parametros\": {}}")
                .when().post("/programa")
                .then()
                .statusCode(400)
                .body("error", is("No existe el PCML noexiste"));
    }

    @Test
    void invalidParametersAreBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"pcml\": \"saludo\", \"parametros\": {\"name\": \"123456789012345678901\"}}")
                .when().post("/programa")
                .then()
                .statusCode(400)
                .body("error", is("El parámetro name admite como máximo 20 caracteres"));
    }
}
