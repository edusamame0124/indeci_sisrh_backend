package com.indeci.rrhh.vinculacion.importacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Aplica todas las {@link ReglaVinculacion} a una fila y consolida los hallazgos.
 *
 * <p><b>Cerrado a modificación (OCP):</b> Spring inyecta todas las reglas registradas;
 * agregar una validación nueva no toca esta clase.
 *
 * <p>No decide si la fila se importa: solo reporta. Esa decisión la toma el caso de uso
 * mirando {@link Resultado#tieneErrores()}.
 */
@Component
public class VinculacionRowValidator {

    private final List<ReglaVinculacion> reglas;

    public VinculacionRowValidator(List<ReglaVinculacion> reglas) {
        this.reglas = reglas.stream()
                .sorted(Comparator.comparingInt(ReglaVinculacion::orden))
                .toList();
    }

    /**
     * Hallazgos de una fila.
     *
     * @param numeroFila fila tal como se ve en Excel (1-based)
     * @param issues     hallazgos ordenados por severidad de la regla que los produjo
     */
    public record Resultado(int numeroFila, List<RowIssue> issues) {

        /** {@code true} si algún hallazgo impide importar la fila. */
        public boolean tieneErrores() {
            return issues.stream().anyMatch(i -> i.severidad() == RowIssue.Severidad.ERROR);
        }

        /** Estado para pintar el chip en la preview: OK / ADVERTENCIA / ERROR. */
        public String estado() {
            if (tieneErrores()) {
                return "ERROR";
            }
            final boolean hayAdvertencia = issues.stream()
                    .anyMatch(i -> i.severidad() == RowIssue.Severidad.ADVERTENCIA);
            return hayAdvertencia ? "ADVERTENCIA" : "OK";
        }
    }

    public Resultado validar(VinculacionRowRaw fila) {
        final List<RowIssue> issues = new ArrayList<>();
        for (ReglaVinculacion regla : reglas) {
            issues.addAll(regla.validar(fila));
        }
        return new Resultado(fila.getNumeroFila(), issues);
    }

    public List<Resultado> validarTodas(List<VinculacionRowRaw> filas) {
        return filas.stream().map(this::validar).toList();
    }
}
