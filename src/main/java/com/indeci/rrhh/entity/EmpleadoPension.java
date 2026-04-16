package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO_PENSION", schema = "GESTIONRRHH")
@Data
public class EmpleadoPension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "AFP_ID")
    private Long afpId;

    @Column(name = "TIPO")
    private String tipo; // AFP / ONP

    @Column(name = "CUSPP")
    private String cuspp;

    @Column(name = "PORCENTAJE_APORTE")
    private Double porcentajeAporte;

    @Column(name = "PORCENTAJE_COMISION")
    private Double porcentajeComision;

    @Column(name = "PORCENTAJE_SEGURO")
    private Double porcentajeSeguro;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}