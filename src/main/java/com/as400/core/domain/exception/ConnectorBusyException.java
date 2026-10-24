package com.as400.core.domain.exception;

/**
 * El conector está saturado: todas las conexiones (o el cupo del grupo del programa) están en uso.
 * A diferencia de {@link As400UnavailableException}, el AS400 puede estar sano.
 */
public class ConnectorBusyException extends RuntimeException {

    public ConnectorBusyException(String message) {
        super(message);
    }
}
