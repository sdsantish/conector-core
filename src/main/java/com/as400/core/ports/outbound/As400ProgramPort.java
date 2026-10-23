package com.as400.core.ports.outbound;

import com.as400.core.domain.ProgramDefinition;

import java.util.Map;

/**
 * Lo que el conector necesita del AS400: llamar a un programa con sus entradas y obtener sus salidas.
 */
public interface As400ProgramPort {

    /**
     * @param inputs entradas ya validadas por {@link com.as400.core.domain.ProgramInputValidator}
     * @return parámetros de salida (output e inputoutput) por nombre
     */
    Map<String, Object> call(ProgramDefinition program, Map<String, Object> inputs);
}
