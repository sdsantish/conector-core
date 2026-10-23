package com.as400.core.domain.exception;

/**
 * No hay conexión disponible con el AS400: no responde o se alcanzó el máximo de conexiones simultáneas.
 */
public class As400UnavailableException extends RuntimeException {

    public As400UnavailableException(String message) {
        super(message);
    }

    public As400UnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
