package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class EmpleadoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "SUELDO_BASICO")
    private Double sueldoBasico;

    @Column(name = "ASIGNACION_MOVILIDAD")
    private Double movilidad;

    @Column(name = "ASIGNACION_ALIMENTACION")
    private Double alimentacion;

    @Column(name = "TIENE_ASIGNACION_FAMILIAR")
    private Integer tieneAsignacionFamiliar;

    @Column(name = "NUM_HIJOS")
    private Integer numHijos;

    @Column(name = "DESCUENTO_BANCO")
    private Double descuentoBanco;

    @Column(name = "DESCUENTO_INSTITUCION")
    private Double descuentoInstitucion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}