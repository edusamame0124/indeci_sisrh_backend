package com.indeci.rrhh.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_SOLICITUD_VACACION_DET",
        schema = "GESTIONRRHH")
@Data
public class SolicitudVacacionDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "TIPO")
    private String tipo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "TOTAL_DIAS")
    private Double totalDias;

    @Column(name = "ACTIVO")
    private Integer activo;
}