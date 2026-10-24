package com.as400.core.domain.exception;

/**
 * No se puede hablar con el AS400: no responde, se cortó la conexión o el circuito de ese programa está abierto.
 */
public class As400UnavailableException extends RuntimeException {

    public As400UnavailableException(String message) {
        super(message);
    }

    public As400UnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
