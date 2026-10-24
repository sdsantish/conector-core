package com.as400.infrastructure.outbound.as400;

import com.as400.config.AS400Config;
import com.as400.core.domain.ProgramDefinition;
import com.as400.core.domain.exception.As400UnavailableException;
import com.as400.core.domain.exception.ProgramDefinitionException;
import com.as400.core.domain.exception.ProgramExecutionException;
import com.as400.core.domain.exception.ProgramTimeoutException;
import com.as400.core.ports.outbound.As400ProgramPort;
import com.as400.infrastructure.outbound.as400.mappers.PcmlDocumentMapper;
import com.as400.infrastructure.outbound.pcml.PcmlResources;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.data.PcmlException;
import com.ibm.as400.data.ProgramCallDocument;
import com.ibm.as400.data.XmlException;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Llama a los programas del AS400 con jt400 a partir de su PCML.
 */
@ApplicationScoped
public class As400LegacyAdapter implements As400ProgramPort {

    private static final Logger LOG = Logger.getLogger(As400LegacyAdapter.class);

    private final As400Connection connection;
    private final AS400Config config;
    // PCML ya parseado por jt400; cada llamada trabaja sobre un clon porque el documento guarda los valores
    private final Map<String, ProgramCallDocument> templates = new ConcurrentHashMap<>();

    public As400LegacyAdapter(As400Connection connection, AS400Config config) {
        this.connection = connection;
        this.config = config;
    }

    @Override
    public Map<String, Object> call(ProgramDefinition program, Map<String, Object> inputs) {
        ProgramCallDocument document = (ProgramCallDocument) template(program.document()).clone();
        // Al vencer, jt400 termina el trabajo del servidor en el AS400 (ENDJOB *IMMED)
        int timeoutSeconds = (int) Math.max(1, config.callTimeout().toSeconds());
        document.setTimeOut(timeoutSeconds);

        return connection.execute(system -> {
            long start = System.nanoTime();
            try {
                document.setSystem(system);
                PcmlDocumentMapper.write(document, program, inputs);

                LOG.debugf("Llamando a %s en %s", program.name(), system.getSystemName());
                boolean ok = document.callProgram(program.name());
                if (!ok && timedOut(start, timeoutSeconds)) {
                    throw timeout(program, timeoutSeconds, null);
                }
                if (!ok) {
                    throw new ProgramExecutionException("El programa " + program.name() + " falló: "
                            + formatMessages(document.getMessageList(program.name())));
                }
                return PcmlDocumentMapper.read(document, program);
            } catch (PcmlException e) {
                if (timedOut(start, timeoutSeconds)) {
                    throw timeout(program, timeoutSeconds, e);
                }
                if (hasCause(e, IOException.class)) {
                    throw new As400UnavailableException("Se perdió la conexión con el AS400 durante la llamada a "
                            + program.name() + ": " + e.getMessage(), e);
                }
                throw new ProgramExecutionException("Error al llamar al programa " + program.name()
                        + ": " + e.getMessage(), e);
            }
        });
    }

    private ProgramCallDocument template(String document) {
        return templates.computeIfAbsent(document, name -> {
            try {
                return new ProgramCallDocument(PcmlResources.documentName(name),
                        Thread.currentThread().getContextClassLoader());
            } catch (XmlException e) {
                throw new ProgramDefinitionException("jt400 no pudo cargar el PCML " + name
                        + ": " + e.getMessage(), e);
            }
        });
    }

    private static boolean timedOut(long start, int timeoutSeconds) {
        return System.nanoTime() - start >= timeoutSeconds * 1_000_000_000L;
    }

    private static ProgramTimeoutException timeout(ProgramDefinition program, int timeoutSeconds, Throwable cause) {
        return new ProgramTimeoutException("El programa " + program.name() + " superó el tiempo máximo de "
                + timeoutSeconds + "s y se canceló en el AS400", cause);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static String formatMessages(AS400Message[] messages) {
        if (messages == null || messages.length == 0) {
            return "sin mensajes";
        }
        return Arrays.stream(messages)
                .map(m -> m.getID() + " - " + m.getText())
                .collect(Collectors.joining("; "));
    }
}
