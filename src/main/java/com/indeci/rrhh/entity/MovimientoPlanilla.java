package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_MOVIMIENTO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class MovimientoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "PERIODO")
    private String periodo;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "TOTAL_INGRESOS")
    private Double totalIngresos;

    @Column(name = "TOTAL_DESCUENTOS")
    private Double totalDescuentos;

    @Column(name = "NETO_PAGAR")
    private Double netoPagar;

    @Column(name = "ESTADO")
    private String estado;
}