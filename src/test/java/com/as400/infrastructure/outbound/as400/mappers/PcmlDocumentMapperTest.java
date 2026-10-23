package com.as400.infrastructure.outbound.as400.mappers;

import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.ProgramInputValidator;
import com.as400.infrastructure.outbound.pcml.PcmlDefinitionRepository;
import com.as400.infrastructure.outbound.pcml.PcmlResources;
import com.ibm.as400.data.ProgramCallDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Escribe en un ProgramCallDocument real de jt400 (sin conectar al AS400) para comprobar que acepta los tipos.
 */
class PcmlDocumentMapperTest {

    private final PcmlDefinitionRepository repository = new PcmlDefinitionRepository();

    @Test
    void writesScalars() throws Exception {
        ProgramCallDocument document = write("suma", Map.of("num1", 10.5, "num2", 4.25));

        assertEquals(0, new BigDecimal("10.5").compareTo((BigDecimal) document.getValue("suma.num1")));
    }

    @Test
    void writesStructsAndArrays() throws Exception {
        ProgramCallDocument document = write("pruebapcml", Map.of("pInput", Map.of(
                "idCliente", 7,
                "nombre", "CARLOS",
                "totalItems", 1,
                "listaProd", List.of(Map.of("idProducto", 10, "cantidad", 3)))));

        assertEquals(7, document.getValue("PRUEBAPCML.PINPUT.IDCLIENTE"));
        assertEquals("CARLOS", document.getValue("PRUEBAPCML.PINPUT.NOMBRE"));
        assertEquals(3, document.getValue("PRUEBAPCML.PINPUT.LISTAPROD.CANTIDAD", new int[]{0}));
        assertEquals(0, document.getValue("PRUEBAPCML.PINPUT.LISTAPROD.CANTIDAD", new int[]{4}));
    }

    @Test
    void clonedDocumentsDoNotShareValues() throws Exception {
        ProgramCallDocument template = new ProgramCallDocument(PcmlResources.documentName("saludo"),
                getClass().getClassLoader());
        ProgramCallDocument first = (ProgramCallDocument) template.clone();
        ProgramCallDocument second = (ProgramCallDocument) template.clone();

        first.setValue("saludo.name", "UNO");
        second.setValue("saludo.name", "DOS");

        assertEquals("UNO", first.getValue("saludo.name"));
        assertEquals("DOS", second.getValue("saludo.name"));
    }

    private ProgramCallDocument write(String name, Map<String, ?> parameters) throws Exception {
        ProgramDefinition program = repository.find(name, null);
        ProgramCallDocument document = new ProgramCallDocument(PcmlResources.documentName(name),
                getClass().getClassLoader());
        PcmlDocumentMapper.write(document, program, ProgramInputValidator.validate(program, parameters));
        return document;
    }
}
