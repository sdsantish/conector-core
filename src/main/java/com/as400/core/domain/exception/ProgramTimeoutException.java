package com.as400.core.domain.exception;

/**
 * El programa superó el tiempo máximo de ejecución y se canceló en el AS400.
 */
public class ProgramTimeoutException extends RuntimeException {

    public ProgramTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
