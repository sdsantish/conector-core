package com.as400.infrastructure.outbound.pcml;

import com.as400.core.domain.ParameterDefinition;
import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.exception.InvalidProgramRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmlDefinitionRepositoryTest {

    private final PcmlDefinitionRepository repository = new PcmlDefinitionRepository();

    @Test
    void readsSimpleProgram() {
        ProgramDefinition program = repository.find("saludo", null);

        assertEquals("saludo", program.name());
        assertEquals(2, program.parameters().size());
        ParameterDefinition name = program.parameters().getFirst();
        assertEquals("char", name.type());
        assertEquals(20, name.length());
        assertTrue(name.requiresInput());
        assertFalse(name.hasOutput());
    }

    @Test
    void resolvesStructsArraysAndInheritedUsage() {
        ProgramDefinition program = repository.find("pruebapcml", "pruebapcml");

        ParameterDefinition input = program.parameters().getFirst();
        assertTrue(input.isStruct());
        assertEquals("inputoutput", input.usage());

        ParameterDefinition products = input.children().get(3);
        assertEquals("LISTAPROD", products.name());
        assertEquals("5", products.count());
        assertEquals("inputoutput", products.children().getFirst().usage());
    }

    @Test
    void readsCountReferences() {
        ParameterDefinition customers = repository.find("clicon", null).parameters().get(2);

        assertEquals("cantidad", customers.count());
        assertTrue(customers.isStruct());
        assertNull(customers.length());
    }

    @Test
    void rejectsUnknownOrUnsafeNames() {
        assertThrows(InvalidProgramRequestException.class, () -> repository.find("noexiste", null));
        assertThrows(InvalidProgramRequestException.class, () -> repository.find("../application", null));
        assertThrows(InvalidProgramRequestException.class, () -> repository.find("saludo", "otro"));
    }
}
