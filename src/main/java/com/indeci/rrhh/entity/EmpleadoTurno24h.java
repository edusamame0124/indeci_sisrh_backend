package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Turno continuo de 24h (guardia COEN, 08:30→08:30) de un trabajador, con
 * vigencia propia. No redefine horario — ese vive en {@link JornadaRegimen}
 * (fila del régimen "COEN"). Solo marca a quién aplicarle el emparejamiento
 * de días consecutivos en la reconciliación de asistencia (ver
 * {@code Turno24hReconciliadorService}).
 */
@Entity
@Table(name = "INDECI_EMPLEADO_TURNO_24H", schema = "GESTIONRRHH")
@Data
public class EmpleadoTurno24h {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "DOCUMENTO_AUTORIZACION", length = 200, nullable = false)
    private String documentoAutorizacion;

    @Column(name = "MOTIVO", length = 500)
    private String motivo;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** ¿Este turno 24h cubre la fecha dada? (espejo de EmpleadoJornadaExcepcion.cubre). */
    public boolean cubre(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
}
