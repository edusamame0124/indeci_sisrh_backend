package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Spec 010 / V010_10 — Encargatura: relación titular ↔ reemplazante.
 * Columnas E/F del Excel operativo.
 */
@Entity
@Table(name = "INDECI_EMPLEADO_ENCARGATURA", schema = "GESTIONRRHH")
@Data
public class EmpleadoEncargatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empleado en su cargo original (titular de la plaza). */
    @Column(name = "EMPLEADO_TITULAR_ID", nullable = false)
    private Long empleadoTitularId;

    /** Empleado que cubre temporalmente al titular. */
    @Column(name = "EMPLEADO_ENCARG_ID", nullable = false)
    private Long empleadoEncargId;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "RESOLUCION")
    private String resolucion;

    /** ACTIVO | CULMINADO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
