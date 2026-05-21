package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Spec 010 / V010_10 — Conciliación monto sistema vs monto AIRHSP (M13).
 */
@Entity
@Table(name = "INDECI_CONCILIACION_AIRHSP", schema = "GESTIONRRHH")
@Data
public class ConciliacionAirhsp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "MOVIMIENTO_PLANILLA_ID", nullable = false)
    private Long movimientoPlanillaId;

    @Column(name = "PERIODO_PLANILLA_ID", nullable = false)
    private Long periodoPlanillaId;

    @Column(name = "MONTO_SISTEMA", nullable = false)
    private Double montoSistema;

    @Column(name = "MONTO_AIRHSP", nullable = false)
    private Double montoAirhsp;

    /**
     * Columna VIRTUAL en BD (GENERATED ALWAYS AS MONTO_SISTEMA - MONTO_AIRHSP).
     * Solo lectura: nunca se inserta ni actualiza desde Java.
     */
    @Column(name = "DIFERENCIA", insertable = false, updatable = false)
    private Double diferencia;

    /** PENDIENTE | CONCILIADO | JUSTIFICADO | RECHAZADO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "JUSTIFICACION")
    private String justificacion;

    @Column(name = "USUARIO_REVISA")
    private Long usuarioRevisa;

    @Column(name = "FECHA_REVISION")
    private LocalDate fechaRevision;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
