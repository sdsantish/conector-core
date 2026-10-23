package com.as400.core.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Nivel del árbol de parámetros que se está recorriendo (los parámetros del programa o los campos de una
 * estructura), con un contexto propio de quien recorre: valores ya validados, ruta en el documento, etc.
 * Permite resolver las referencias de count como lo hace PCML: buscando el nombre desde el nivel actual hacia arriba.
 */
public record DefinitionScope<T>(DefinitionScope<T> parent, String program, List<ParameterDefinition> parameters,
                                 T context) {

    /**
     * Parámetro referenciado y nivel donde está declarado.
     *
     * @param path nombres desde ese nivel hasta el parámetro (más de uno si la referencia entra en estructuras)
     */
    public record Reference<T>(DefinitionScope<T> scope, List<String> path) {
    }

    public static <T> DefinitionScope<T> root(ProgramDefinition program, T context) {
        return new DefinitionScope<>(null, program.name(), program.parameters(), context);
    }

    public DefinitionScope<T> child(List<ParameterDefinition> fields, T childContext) {
        return new DefinitionScope<>(this, program, fields, childContext);
    }

    public Optional<Reference<T>> resolve(String reference) {
        List<String> path = Arrays.asList(reference.split("\\."));
        for (DefinitionScope<T> scope = this; scope != null; scope = scope.parent()) {
            if (scope.declares(path.getFirst())) {
                return Optional.of(new Reference<>(scope, path));
            }
            // Referencia calificada con el nombre del programa
            if (scope.parent() == null && path.size() > 1 && path.getFirst().equals(program)) {
                return Optional.of(new Reference<>(scope, path.subList(1, path.size())));
            }
        }
        return Optional.empty();
    }

    private boolean declares(String name) {
        return parameters.stream().anyMatch(parameter -> parameter.name().equals(name));
    }
}
