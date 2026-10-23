package com.as400.core.domain;

import com.as400.core.domain.exception.InvalidProgramRequestException;
import com.as400.infrastructure.outbound.pcml.PcmlDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramInputValidatorTest {

    private final PcmlDefinitionRepository repository = new PcmlDefinitionRepository();

    @Test
    void keepsOnlyInputsWithDefinitionNames() {
        Map<String, Object> inputs = validate("saludo", Map.of("NAME", "CARLOS"));

        assertEquals(Map.of("name", "CARLOS"), inputs);
    }

    @Test
    void convertsDecimals() {
        Map<String, Object> inputs = validate("suma", Map.of("num1", 10.5, "num2", "4.25"));

        assertEquals(new BigDecimal("10.5"), inputs.get("num1"));
        assertEquals(new BigDecimal("4.25"), inputs.get("num2"));
    }

    @Test
    void padsArraysAndFillsOptionalInputOutputParameters() {
        Map<String, Object> inputs = validate("pruebapcml", Map.of("pInput", Map.of(
                "idCliente", 1,
                "nombre", "CARLOS",
                "totalItems", 2,
                "listaProd", List.of(Map.of("idProducto", 10, "cantidad", 3)))));

        Map<?, ?> input = (Map<?, ?>) inputs.get("PINPUT");
        List<?> products = (List<?>) input.get("LISTAPROD");
        assertEquals(5, products.size());
        assertEquals(Map.of("IDPRODUCTO", 10L, "CANTIDAD", 3L), products.get(0));
        assertEquals(Map.of("IDPRODUCTO", 0L, "CANTIDAD", 0L), products.get(4));
        // POUTPUT es inputoutput: no se envió y se rellena
        assertEquals(Map.of("STATUS", "", "MSG", "", "TOTALCARRITO", 0L), inputs.get("POUTPUT"));
    }

    @Test
    void ignoresOutputOnlyParameters() {
        Map<String, Object> inputs = validate("clicon", Map.of("ciudad", "LIMA"));

        assertEquals(Map.of("ciudad", "LIMA"), inputs);
        assertFalse(inputs.containsKey("clientes"));
    }

    @Test
    void rejectsInvalidRequests() {
        assertMessage("Falta el parámetro name", "saludo", Map.of());
        assertMessage("El parámetro name admite como máximo 20 caracteres", "saludo",
                Map.of("name", "123456789012345678901"));
        assertMessage("Parámetros no definidos en el PCML: [OTRO]", "saludo", Map.of("name", "A", "otro", 1));
        assertMessage("El parámetro num1 admite como máximo 5 decimales", "suma",
                Map.of("num1", "1.123456", "num2", 1));
        assertMessage("El parámetro num1 admite como máximo 10 dígitos enteros", "suma",
                Map.of("num1", "12345678901", "num2", 1));
        assertMessage("El parámetro num1 no es un packed válido: abc", "suma", Map.of("num1", "abc", "num2", 1));
        assertMessage("El parámetro PINPUT.LISTAPROD admite como máximo 5 elementos", "pruebapcml",
                Map.of("pInput", Map.of("listaProd", List.of(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                        Map.of()))));
        assertMessage("El parámetro PINPUT.IDCLIENTE debe estar entre -2147483648 y 2147483647", "pruebapcml",
                Map.of("pInput", Map.of("idCliente", 3_000_000_000L)));
        assertMessage("El parámetro PINPUT debe ser un objeto", "pruebapcml", Map.of("pInput", "texto"));
    }

    private Map<String, Object> validate(String document, Map<String, ?> parameters) {
        return ProgramInputValidator.validate(repository.find(document, null), parameters);
    }

    private void assertMessage(String expected, String document, Map<String, ?> parameters) {
        InvalidProgramRequestException e = assertThrows(InvalidProgramRequestException.class,
                () -> validate(document, parameters));
        assertTrue(e.getMessage().equals(expected), () -> "Mensaje: " + e.getMessage());
    }
}
