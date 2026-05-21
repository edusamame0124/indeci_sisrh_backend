package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Resumen de asistencia de un empleado en un período (M04 / SPEC §12.2 PANTALLA-02).
 * UPSERT: una sola fila por (empleadoId, periodo).
 */
@Entity
@Table(name = "INDECI_ASISTENCIA_CABECERA", schema = "GESTIONRRHH")
@Data
public class AsistenciaCabecera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "PERIODO")
    private String periodo;

    @Column(name = "REMUNERACION_BASE")
    private Double remuneracionBase;

    @Column(name = "DIAS_LABORADOS")
    private Integer diasLaborados;

    @Column(name = "DIAS_FALTA")
    private Integer diasFalta;

    @Column(name = "TOTAL_MIN_TARDANZA")
    private Integer totalMinTardanza;

    @Column(name = "DESCUENTO_TARDANZA")
    private Double descuentoTardanza;

    @Column(name = "DESCUENTO_FALTA")
    private Double descuentoFalta;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
