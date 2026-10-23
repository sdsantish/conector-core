package com.as400.infrastructure.outbound.pcml;

/**
 * Ubicación de los PCML en el classpath: src/main/resources/pcml/&lt;nombre&gt;.pcml.
 */
public final class PcmlResources {

    private static final String FOLDER = "pcml";

    private PcmlResources() {
    }

    public static String resourcePath(String document) {
        return FOLDER + "/" + document + ".pcml";
    }

    /**
     * Nombre tal como lo espera jt400 (p. ej. pcml.suma), que lo traduce a pcml/suma.pcml.
     */
    public static String documentName(String document) {
        return FOLDER + "." + document;
    }
}
