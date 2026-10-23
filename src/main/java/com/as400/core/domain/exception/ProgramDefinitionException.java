package com.as400.core.domain.exception;

/**
 * La definición del programa (el .pcml) está mal formada o usa algo no soportado.
 */
public class ProgramDefinitionException extends RuntimeException {

    public ProgramDefinitionException(String message) {
        super(message);
    }

    public ProgramDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
