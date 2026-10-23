package com.as400.core.domain;

import java.util.List;

/**
 * Parámetro de un programa del AS400: un dato simple o una estructura con sus campos.
 *
 * @param type      tipo AS400 (char, packed, zoned, int, float) o "struct"
 * @param length    longitud declarada, o null si no es un número fijo
 * @param precision precisión declarada, o null si no se indica
 * @param usage     usage efectivo (input, output o inputoutput), ya resuelto el inherit
 * @param count     tamaño del arreglo (número o referencia a otro parámetro), o null si no es arreglo
 * @param init      true si la definición trae un valor inicial, por lo que la entrada es opcional
 * @param children  campos de la estructura, o null si es un dato simple
 */
public record ParameterDefinition(String name, String type, Integer length, Integer precision, String usage,
                                  String count, boolean init, List<ParameterDefinition> children) {

    public boolean isStruct() {
        return children != null;
    }

    public boolean isArray() {
        return count != null;
    }

    // Una estructura es de entrada/salida si alguno de sus campos lo es
    public boolean hasInput() {
        return isStruct() ? children.stream().anyMatch(ParameterDefinition::hasInput) : !"output".equals(usage);
    }

    /**
     * Solo los datos de usage input sin init deben venir en la petición; los inputoutput
     * (como los que genera el compilador RPG) se rellenan con blancos/ceros si faltan.
     */
    public boolean requiresInput() {
        return isStruct()
                ? children.stream().anyMatch(ParameterDefinition::requiresInput)
                : "input".equals(usage) && !init;
    }

    public boolean hasOutput() {
        return isStruct() ? children.stream().anyMatch(ParameterDefinition::hasOutput) : !"input".equals(usage);
    }
}
