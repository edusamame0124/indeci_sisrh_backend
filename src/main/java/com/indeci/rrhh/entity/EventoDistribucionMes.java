package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P0 maternidad — Tramo mensual del descanso subsidiado.
 * El motor P0 usa {@code DIAS_SUBSIDIO} para días laborados por periodo.
 */
@Entity
@Table(name = "INDECI_EVENTO_DISTRIBUCION_MES")
@Data
public class EventoDistribucionMes {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_EVENTO_DIST_MES")
    @SequenceGenerator(name = "SEQ_EVENTO_DIST_MES", sequenceName = "SEQ_EVENTO_DIST_MES", allocationSize = 1)
    private Long id;

    @Column(name = "EMPLEADO_EVENTO_ID", nullable = false)
    private Long empleadoEventoId;

    @Column(name = "PERIODO", nullable = false, length = 6)
    private String periodo;

    @Column(name = "FECHA_DESDE", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "FECHA_HASTA", nullable = false)
    private LocalDate fechaHasta;

    @Column(name = "DIAS_SUBSIDIO", nullable = false)
    private Integer diasSubsidio;

    @Column(name = "AFECTA_DIAS_LABORADOS", nullable = false, length = 1)
    private String afectaDiasLaborados = "S";

    @Column(name = "ESTADO_TRAMO", nullable = false, length = 30)
    private String estadoTramo = "PENDIENTE_IMPUTACION";

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
