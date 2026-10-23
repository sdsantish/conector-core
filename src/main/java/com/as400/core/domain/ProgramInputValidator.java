package com.as400.core.domain;

import com.as400.core.domain.exception.InvalidProgramRequestException;
import com.as400.core.domain.exception.ProgramDefinitionException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Valida los parámetros recibidos contra la definición del programa y los deja listos para enviarse:
 * <ul>
 *   <li>claves con el nombre exacto de la definición (en la petición no se distinguen mayúsculas);</li>
 *   <li>estructuras como {@code Map}, arreglos como {@code List} con exactamente {@code count} elementos
 *       (los que faltan se rellenan con blancos/ceros);</li>
 *   <li>valores como {@code String} (char), {@code BigDecimal} (packed/zoned), {@code Long} (int) o
 *       {@code Double} (float);</li>
 *   <li>un dato con init que no vino se omite (clave ausente, o null dentro de un arreglo).</li>
 * </ul>
 */
public final class ProgramInputValidator {

    private ProgramInputValidator() {
    }

    public static Map<String, Object> validate(ProgramDefinition program, Map<String, ?> parameters) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        writeFields(DefinitionScope.root(program, inputs), "", parameters == null ? Map.of() : parameters, false);
        return inputs;
    }

    /**
     * @param scope    nivel actual; su contexto es el mapa donde se dejan los valores validados
     * @param field    ruta del nivel para los mensajes de error (p. ej. clientes[2])
     * @param defaults true al rellenar elementos de arreglo que no vinieron en la petición
     */
    private static void writeFields(DefinitionScope<Map<String, Object>> scope, String field, Map<?, ?> values,
                                    boolean defaults) {
        Map<String, Object> byName = new LinkedHashMap<>();
        values.forEach((key, value) -> byName.put(key.toString().toUpperCase(Locale.ROOT), value));

        Set<String> unknown = new HashSet<>(byName.keySet());
        for (ParameterDefinition parameter : scope.parameters()) {
            if (!parameter.hasInput()) {
                continue;
            }
            String name = parameter.name().toUpperCase(Locale.ROOT);
            unknown.remove(name);
            writeParameter(scope, parameter, join(field, parameter.name()), byName.get(name), defaults);
        }
        if (!unknown.isEmpty()) {
            throw new InvalidProgramRequestException("Parámetros no definidos en el PCML"
                    + (field.isEmpty() ? "" : " dentro de " + field) + ": " + unknown);
        }
    }

    private static void writeParameter(DefinitionScope<Map<String, Object>> scope, ParameterDefinition parameter,
                                       String field, Object value, boolean defaults) {
        if (!parameter.isArray()) {
            Object element = element(scope, parameter, field, value, defaults);
            if (element != null) {
                scope.context().put(parameter.name(), element);
            }
            return;
        }

        int count = count(scope, parameter, field);
        if (value == null && !defaults && parameter.requiresInput()) {
            throw missing(field);
        }
        List<?> items = value == null ? List.of() : list(value, field);
        if (items.size() > count) {
            throw new InvalidProgramRequestException("El parámetro " + field
                    + " admite como máximo " + count + " elementos");
        }
        // El programa recibe siempre count elementos: los que faltan se rellenan con blancos/ceros
        List<Object> elements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean absent = i >= items.size();
            elements.add(element(scope, parameter, field + "[" + i + "]",
                    absent ? null : items.get(i), defaults || absent));
        }
        scope.context().put(parameter.name(), elements);
    }

    /**
     * @return el valor validado, o null si es un dato con init que no vino
     */
    private static Object element(DefinitionScope<Map<String, Object>> scope, ParameterDefinition parameter,
                                  String field, Object value, boolean defaults) {
        if (parameter.isStruct()) {
            if (value == null && !defaults && parameter.requiresInput()) {
                throw missing(field);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            writeFields(scope.child(parameter.children(), fields), field,
                    value == null ? Map.of() : map(value, field), defaults || value == null);
            return fields;
        }

        if (value == null) {
            if (parameter.init()) {
                return null;
            }
            if (!defaults && parameter.requiresInput()) {
                throw missing(field);
            }
            return convert(parameter, field, "char".equals(parameter.type()) ? "" : 0);
        }
        return convert(parameter, field, value);
    }

    // --- Tamaño de arreglos ---

    private static int count(DefinitionScope<Map<String, Object>> scope, ParameterDefinition parameter,
                             String field) {
        String count = parameter.count();
        if (count.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(count);
        }
        // count referencia a otro parámetro de entrada ya validado
        DefinitionScope.Reference<Map<String, Object>> reference = scope.resolve(count)
                .orElseThrow(() -> new ProgramDefinitionException(
                        "No se encontró el elemento " + count + " usado como count de " + parameter.name()));
        Object value = reference.scope().context();
        for (String name : reference.path()) {
            value = value instanceof Map<?, ?> fields ? fields.get(name) : null;
        }
        if (!(value instanceof Number number) || number.intValue() < 0) {
            throw new InvalidProgramRequestException("El tamaño de " + field + " depende de " + count
                    + ", que debe enviarse con un valor de 0 o más");
        }
        return number.intValue();
    }

    // --- Conversión de valores ---

    private static Object convert(ParameterDefinition parameter, String field, Object value) {
        try {
            return switch (parameter.type()) {
                case "char" -> text(parameter, field, value);
                case "packed", "zoned" -> decimal(parameter, field, value);
                case "int" -> integer(parameter, field, value);
                case "float" -> Double.valueOf(value.toString());
                default -> throw new InvalidProgramRequestException(
                        "Tipo " + parameter.type() + " del parámetro " + field + " no soportado");
            };
        } catch (NumberFormatException | ArithmeticException e) {
            throw new InvalidProgramRequestException("El parámetro " + field + " no es un "
                    + parameter.type() + " válido: " + value);
        }
    }

    private static String text(ParameterDefinition parameter, String field, Object value) {
        String text = value.toString();
        if (parameter.length() != null && text.length() > parameter.length()) {
            throw new InvalidProgramRequestException("El parámetro " + field
                    + " admite como máximo " + parameter.length() + " caracteres");
        }
        return text;
    }

    private static BigDecimal decimal(ParameterDefinition parameter, String field, Object value) {
        BigDecimal number = new BigDecimal(value.toString());
        int scale = parameter.precision() == null ? 0 : parameter.precision();
        if (number.stripTrailingZeros().scale() > scale) {
            throw new InvalidProgramRequestException("El parámetro " + field
                    + " admite como máximo " + scale + " decimales");
        }
        if (parameter.length() != null) {
            int integerDigits = parameter.length() - scale;
            if (number.abs().compareTo(BigDecimal.TEN.pow(integerDigits)) >= 0) {
                throw new InvalidProgramRequestException("El parámetro " + field
                        + " admite como máximo " + integerDigits + " dígitos enteros");
            }
        }
        return number;
    }

    // El rango depende de la longitud en bytes y de si es sin signo (precision = bits)
    private static Long integer(ParameterDefinition parameter, String field, Object value) {
        long number = new BigDecimal(value.toString()).longValueExact();
        int length = parameter.length() == null ? 4 : parameter.length();
        if (length >= 8) {
            return number;
        }
        int bits = length * 8;
        boolean unsigned = parameter.precision() != null && parameter.precision() == bits;
        long min = unsigned ? 0 : -(1L << (bits - 1));
        long max = unsigned ? (1L << bits) - 1 : (1L << (bits - 1)) - 1;
        if (number < min || number > max) {
            throw new InvalidProgramRequestException("El parámetro " + field
                    + " debe estar entre " + min + " y " + max);
        }
        return number;
    }

    // --- Utilidades ---

    private static InvalidProgramRequestException missing(String field) {
        return new InvalidProgramRequestException("Falta el parámetro " + field);
    }

    private static String join(String field, String name) {
        return field.isEmpty() ? name : field + "." + name;
    }

    private static List<?> list(Object value, String field) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new InvalidProgramRequestException("El parámetro " + field + " debe ser una lista");
    }

    private static Map<?, ?> map(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new InvalidProgramRequestException("El parámetro " + field + " debe ser un objeto");
    }
}
