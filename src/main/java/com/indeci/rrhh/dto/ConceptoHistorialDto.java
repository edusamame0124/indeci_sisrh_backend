package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPEC_CONCEPTOS_PLANILLA §12 / P3 — historial completo de un concepto.
 *
 * <p>Agrupa, para un mismo CÓDIGO:</p>
 * <ul>
 *   <li>{@code versiones}: línea de tiempo de versiones (n.º, rango de vigencia,
 *       estado, marca de vigente).</li>
 *   <li>{@code auditoria}: entradas del log de auditoría (CREAR/ACTUALIZAR/
 *       ACTIVAR/CERRAR/ANULAR…) asociadas a las filas de ese código.</li>
 * </ul>
 */
@Data
public class ConceptoHistorialDto {

    private List<VersionItem> versiones;
    private List<AuditoriaItem> auditoria;

    @Data
    public static class VersionItem {
        private Long id;
        private Integer version;
        private LocalDate vigIni;
        private LocalDate vigFin;
        private String estado;
        /** {@code true} si es la versión vigente (estado ACTIVO). */
        private boolean vigente;
    }

    @Data
    public static class AuditoriaItem {
        private String accion;
        private String usuario;
        private LocalDateTime fecha;
        private String detalle;
    }
}
