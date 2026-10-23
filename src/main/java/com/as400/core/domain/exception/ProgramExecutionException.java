package com.as400.core.domain.exception;

/**
 * El AS400 recibió la llamada pero el programa falló o no se pudo ejecutar.
 */
public class ProgramExecutionException extends RuntimeException {

    public ProgramExecutionException(String message) {
        super(message);
    }

    public ProgramExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
