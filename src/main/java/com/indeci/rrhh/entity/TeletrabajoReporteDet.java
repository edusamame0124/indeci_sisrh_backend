package com.indeci.rrhh.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_TELETRABAJO_REPORTE_DET",
        schema = "GESTIONRRHH")
@Data
public class TeletrabajoReporteDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REPORTE_ID")
    private Long reporteId;

    @Column(name = "NRO_ORDEN")
    private Integer nroOrden;

    @Column(name = "ACTIVIDAD_PROGRAMADA")
    private String actividadProgramada;

    @Column(name = "ACTIVIDAD_EJECUTADA")
    private String actividadEjecutada;

    @Column(name = "MEDIO_VERIFICACION")
    private String medioVerificacion;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "ESTADO_CUMPLIMIENTO_ID")
    private Long estadoCumplimientoId;

    @Column(name = "PORCENTAJE_AVANCE")
    private Double porcentajeAvance;

    @Column(name = "INCIDENCIA_OBSERVACION")
    private String incidenciaObservacion;

    @Column(name = "CONFORMIDAD_ID")
    private Long conformidadId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ESTADO_CUMPLIMIENTO_ID",
            insertable = false,
            updatable = false)
    private TtEstadoCumplimiento estadoCumplimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "CONFORMIDAD_ID",
            insertable = false,
            updatable = false)
    private TtConformidad conformidad;
}