package com.as400.infrastructure.inbound.http;

import java.util.Map;

/**
 * Body de POST /programa.
 *
 * @param pcml       nombre del archivo en src/main/resources/pcml, sin extensión (p. ej. "suma")
 * @param programa   programa dentro del PCML; opcional si el PCML define uno solo
 * @param parametros valores de los parámetros de entrada, por nombre
 */
public record ProgramRequestDto(String pcml, String programa, Map<String, Object> parametros) {
}
