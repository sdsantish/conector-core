package com.as400.infrastructure.outbound.as400.mappers;

import com.as400.core.domain.DefinitionScope;
import com.as400.core.domain.ParameterDefinition;
import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.exception.ProgramDefinitionException;
import com.ibm.as400.data.PcmlException;
import com.ibm.as400.data.ProgramCallDocument;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capa anticorrupción: traduce entre los valores del dominio (mapas, listas, String, BigDecimal, Long, Double)
 * y el ProgramCallDocument de jt400, que trabaja con rutas calificadas, índices y tipos Java concretos.
 */
public final class PcmlDocumentMapper {

    /**
     * Posición en el documento: nombre calificado (p. ej. programa.clientes) e índices de los arreglos que lo
     * contienen.
     */
    private record Location(String path, int[] indices) {
    }

    private PcmlDocumentMapper() {
    }

    // --- Entradas ---

    public static void write(ProgramCallDocument document, ProgramDefinition program, Map<String, Object> inputs)
            throws PcmlException {
        writeFields(document, program.name(), new int[0], program.parameters(), inputs);
    }

    private static void writeFields(ProgramCallDocument document, String path, int[] indices,
                                    List<ParameterDefinition> parameters, Map<?, ?> values) throws PcmlException {
        for (ParameterDefinition parameter : parameters) {
            // Sin valor: no es de entrada o tiene init en el PCML
            if (!values.containsKey(parameter.name())) {
                continue;
            }
            String parameterPath = path + "." + parameter.name();
            Object value = values.get(parameter.name());
            if (!parameter.isArray()) {
                writeElement(document, parameterPath, indices, parameter, value);
                continue;
            }
            List<?> elements = (List<?>) value;
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i) != null) {
                    writeElement(document, parameterPath, append(indices, i), parameter, elements.get(i));
                }
            }
        }
    }

    private static void writeElement(ProgramCallDocument document, String path, int[] indices,
                                     ParameterDefinition parameter, Object value) throws PcmlException {
        if (parameter.isStruct()) {
            writeFields(document, path, indices, parameter.children(), (Map<?, ?>) value);
        } else {
            document.setValue(path, indices, toAs400(parameter, value));
        }
    }

    // jt400 exige el tipo Java exacto según tipo, longitud y signo (precision = bits si es sin signo)
    private static Object toAs400(ParameterDefinition parameter, Object value) {
        return switch (parameter.type()) {
            case "int" -> {
                long number = (Long) value;
                int length = parameter.length() == null ? 4 : parameter.length();
                boolean unsigned = parameter.precision() != null && parameter.precision() == length * 8;
                yield switch (length) {
                    case 1 -> unsigned ? (Object) (short) number : (Object) (byte) number;
                    case 2 -> unsigned ? (Object) (int) number : (Object) (short) number;
                    case 4 -> unsigned ? (Object) number : (Object) (int) number;
                    default -> unsigned ? (Object) BigInteger.valueOf(number) : (Object) number;
                };
            }
            case "float" -> Integer.valueOf(4).equals(parameter.length())
                    ? (Object) ((Double) value).floatValue()
                    : value;
            case "packed", "zoned" -> (BigDecimal) value;
            default -> value;
        };
    }

    // --- Salidas ---

    public static Map<String, Object> read(ProgramCallDocument document, ProgramDefinition program)
            throws PcmlException {
        return readFields(document, DefinitionScope.root(program, new Location(program.name(), new int[0])));
    }

    private static Map<String, Object> readFields(ProgramCallDocument document, DefinitionScope<Location> scope)
            throws PcmlException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ParameterDefinition parameter : scope.parameters()) {
            if (parameter.hasOutput()) {
                result.put(parameter.name(), readParameter(document, scope, parameter));
            }
        }
        return result;
    }

    private static Object readParameter(ProgramCallDocument document, DefinitionScope<Location> scope,
                                        ParameterDefinition parameter) throws PcmlException {
        String path = scope.context().path() + "." + parameter.name();
        if (!parameter.isArray()) {
            return readElement(document, scope, parameter, path, scope.context().indices());
        }
        // Si count referencia a un campo de salida (p. ej. registros devueltos), solo se leen esos
        int count = count(document, scope, parameter);
        List<Object> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            elements.add(readElement(document, scope, parameter, path, append(scope.context().indices(), i)));
        }
        return elements;
    }

    private static Object readElement(ProgramCallDocument document, DefinitionScope<Location> scope,
                                      ParameterDefinition parameter, String path, int[] indices)
            throws PcmlException {
        if (parameter.isStruct()) {
            return readFields(document, scope.child(parameter.children(), new Location(path, indices)));
        }
        Object value = document.getValue(path, indices);
        // Los char del AS400 vienen rellenos con blancos hasta su longitud
        return value instanceof String text ? text.stripTrailing() : value;
    }

    private static int count(ProgramCallDocument document, DefinitionScope<Location> scope,
                             ParameterDefinition parameter) throws PcmlException {
        String count = parameter.count();
        if (count.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(count);
        }
        DefinitionScope.Reference<Location> reference = scope.resolve(count)
                .orElseThrow(() -> new ProgramDefinitionException(
                        "No se encontró el elemento " + count + " usado como count de " + parameter.name()));
        Location location = reference.scope().context();
        String path = location.path() + "." + String.join(".", reference.path());
        return ((Number) document.getValue(path, location.indices())).intValue();
    }

    private static int[] append(int[] indices, int index) {
        int[] result = Arrays.copyOf(indices, indices.length + 1);
        result[indices.length] = index;
        return result;
    }
}
