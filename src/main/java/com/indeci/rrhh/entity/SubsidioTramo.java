package com.indeci.rrhh.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_TRAMO")
@Data
public class SubsidioTramo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CASO_ID", nullable = false)
    private Long casoId;

    @Column(name = "CITT_ID")
    private Long cittId;

    @Column(name = "PERIODO", nullable = false, length = 6)
    private String periodo;

    @Column(name = "FECHA_DESDE", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "FECHA_HASTA", nullable = false)
    private LocalDate fechaHasta;

    @Column(name = "DIAS_SUBSIDIO", nullable = false)
    private Integer diasSubsidio;

    @Column(name = "DIAS_LABORADOS", nullable = false)
    private Integer diasLaborados;

    @Column(name = "ESTADO_TRAMO", nullable = false, length = 30)
    private String estadoTramo;

    @Column(name = "VERSION_TRAMO", nullable = false)
    private Integer versionTramo;

    @Column(name = "ES_VIGENTE", nullable = false, length = 1)
    private String esVigente;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
