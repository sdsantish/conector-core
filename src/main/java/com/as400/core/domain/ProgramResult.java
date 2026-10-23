package com.as400.core.domain;

import java.util.Map;

/**
 * Resultado de la ejecución: los parámetros de salida del programa, por nombre.
 */
public record ProgramResult(String document, String program, Map<String, Object> outputs) {
}
