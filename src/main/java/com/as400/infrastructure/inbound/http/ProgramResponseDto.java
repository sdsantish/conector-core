package com.as400.infrastructure.inbound.http;

import java.util.Map;

/**
 * Respuesta de POST /programa: los parámetros de salida del programa, por nombre.
 */
public record ProgramResponseDto(String pcml, String programa, Map<String, Object> resultado) {
}
