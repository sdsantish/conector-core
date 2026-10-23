package com.as400.infrastructure.outbound.pcml;

import com.as400.core.domain.ParameterDefinition;
import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.exception.InvalidProgramRequestException;
import com.as400.core.domain.exception.ProgramDefinitionException;
import com.as400.core.ports.outbound.ProgramDefinitionPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Lee las definiciones de programas de los PCML del classpath y las guarda en memoria.
 */
@ApplicationScoped
public class PcmlDefinitionRepository implements ProgramDefinitionPort {

    // Evita que el nombre recibido en el body apunte fuera de la carpeta de PCMLs
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]+");
    // Corta structs que se referencian a sí mismas
    private static final int MAX_DEPTH = 32;

    private final Map<String, Map<String, ProgramDefinition>> cache = new ConcurrentHashMap<>();

    @Override
    public ProgramDefinition find(String document, String program) {
        if (document == null || !VALID_NAME.matcher(document).matches()) {
            throw new InvalidProgramRequestException("Nombre de PCML inválido: " + document);
        }
        Map<String, ProgramDefinition> programs = cache.computeIfAbsent(document, this::load);

        if (program == null || program.isBlank()) {
            if (programs.size() != 1) {
                throw new InvalidProgramRequestException("El PCML " + document
                        + " define varios programas, indica cuál en 'programa': " + programs.keySet());
            }
            return programs.values().iterator().next();
        }
        return programs.values().stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(program))
                .findFirst()
                .orElseThrow(() -> new InvalidProgramRequestException("El PCML " + document
                        + " no define el programa " + program + ", disponibles: " + programs.keySet()));
    }

    private Map<String, ProgramDefinition> load(String document) {
        String resource = PcmlResources.resourcePath(document);
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new InvalidProgramRequestException("No existe el PCML " + document);
            }
            Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            List<Element> topLevel = elements(xml.getDocumentElement());

            // Las struct de primer nivel se pueden usar desde cualquier programa con type="struct"
            Map<String, Element> structs = new HashMap<>();
            for (Element element : topLevel) {
                if ("struct".equals(element.getTagName())) {
                    structs.put(element.getAttribute("name"), element);
                }
            }

            Map<String, ProgramDefinition> programs = new LinkedHashMap<>();
            for (Element element : topLevel) {
                if ("program".equals(element.getTagName())) {
                    String name = element.getAttribute("name");
                    programs.put(name, new ProgramDefinition(document, name,
                            children(document, element, "inputoutput", structs, 0)));
                }
            }
            return programs;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ProgramDefinitionException("No se pudo leer el PCML " + document, e);
        }
    }

    private static List<ParameterDefinition> children(String document, Element parent, String parentUsage,
                                                      Map<String, Element> structs, int depth) {
        if (depth > MAX_DEPTH) {
            throw new ProgramDefinitionException("El PCML " + document + " tiene structs anidadas más de "
                    + MAX_DEPTH + " niveles, ¿una struct se referencia a sí misma?");
        }
        List<ParameterDefinition> parameters = new ArrayList<>();
        for (Element child : elements(parent)) {
            String usage = usage(child, parentUsage);
            String name = child.getAttribute("name");

            if ("struct".equals(child.getTagName())) {
                // struct definida dentro del programa
                parameters.add(new ParameterDefinition(name, "struct", null, null, usage, count(child), false,
                        children(document, child, usage, structs, depth + 1)));
            } else if ("data".equals(child.getTagName()) && "struct".equals(child.getAttribute("type"))) {
                // data que referencia una struct de primer nivel; su usage manda sobre el de la struct
                Element definition = structs.get(child.getAttribute("struct"));
                if (definition == null) {
                    throw new ProgramDefinitionException("El PCML " + document + " no define la struct "
                            + child.getAttribute("struct") + " usada por " + name);
                }
                String structUsage = usage(child, usage(definition, parentUsage));
                parameters.add(new ParameterDefinition(name, "struct", null, null, structUsage, count(child), false,
                        children(document, definition, structUsage, structs, depth + 1)));
            } else if ("data".equals(child.getTagName())) {
                parameters.add(new ParameterDefinition(name, child.getAttribute("type"),
                        integer(child.getAttribute("length")), integer(child.getAttribute("precision")),
                        usage, count(child), child.hasAttribute("init"), null));
            } else {
                throw new ProgramDefinitionException("El PCML " + document + " usa <" + child.getTagName()
                        + ">, que no está soportado");
            }
        }
        return parameters;
    }

    private static List<Element> elements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    // Sin usage o con "inherit" se hereda el del padre
    private static String usage(Element element, String parentUsage) {
        String usage = element.getAttribute("usage");
        return usage.isEmpty() || "inherit".equals(usage) ? parentUsage : usage;
    }

    private static String count(Element element) {
        String count = element.getAttribute("count");
        return count.isEmpty() ? null : count;
    }

    // length/precision pueden referenciar a otro campo; en ese caso no se valida
    private static Integer integer(String value) {
        try {
            return value.isEmpty() ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
