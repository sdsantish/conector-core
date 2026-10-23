package com.as400.core.ports.outbound;

import com.as400.core.domain.ProgramDefinition;

/**
 * De dónde se obtienen las definiciones de los programas (hoy, archivos PCML).
 */
public interface ProgramDefinitionPort {

    /**
     * @param document documento que define el programa (el .pcml, sin extensión)
     * @param program  programa dentro del documento; si es null y define uno solo, se usa ese
     */
    ProgramDefinition find(String document, String program);
}
