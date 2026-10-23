package com.as400.core.domain;

import java.util.Map;

/**
 * Petición de ejecución de un programa.
 *
 * @param document   documento que define el programa (el .pcml, sin extensión)
 * @param program    programa dentro del documento; null si define uno solo
 * @param parameters valores de entrada por nombre: objetos para estructuras, listas para arreglos
 */
public record ProgramRequest(String document, String program, Map<String, Object> parameters) {
}
