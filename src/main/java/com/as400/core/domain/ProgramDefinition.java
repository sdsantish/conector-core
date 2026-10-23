package com.as400.core.domain;

import java.util.List;

/**
 * Programa del AS400 y sus parámetros en el orden en que los recibe.
 *
 * @param document nombre del documento que lo define (el .pcml, sin extensión)
 */
public record ProgramDefinition(String document, String name, List<ParameterDefinition> parameters) {
}
