package com.as400.core.ports.inbound;

import com.as400.core.domain.ProgramRequest;
import com.as400.core.domain.ProgramResult;

/**
 * Lo que el conector ofrece: ejecutar cualquier programa del AS400 que tenga definición.
 */
public interface ExecuteProgramUseCase {

    ProgramResult execute(ProgramRequest request);
}
