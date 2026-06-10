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
        name = "INDECI_SOLICITUD_COMPENSACION_DET",
        schema = "GESTIONRRHH")
@Data
public class SolicitudCompensacionDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "FECHA_COMPENSACION")
    private LocalDate fechaCompensacion;

    @Column(name = "HORA_INICIO")
    private String horaInicio;

    @Column(name = "HORA_FIN")
    private String horaFin;

    @Column(name = "CANTIDAD_HORAS")
    private Double cantidadHoras;

    @Column(name = "ACTIVO")
    private Integer activo;
}