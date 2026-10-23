package com.as400.core.domain.exception;

/**
 * La petición no corresponde con la definición del programa: documento inexistente, parámetros faltantes,
 * desconocidos o con valores que no caben.
 */
public class InvalidProgramRequestException extends RuntimeException {

    public InvalidProgramRequestException(String message) {
        super(message);
    }
}
