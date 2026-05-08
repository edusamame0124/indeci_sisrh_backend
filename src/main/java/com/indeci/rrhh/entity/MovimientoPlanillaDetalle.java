package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_MOVIMIENTO_PLANILLA_DET",
        schema = "GESTIONRRHH"
)
@Data
public class MovimientoPlanillaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MOVIMIENTO_PLANILLA_ID")
    private Long movimientoPlanillaId;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "MONTO")
    private Double monto;

    @Column(name = "CANTIDAD")
    private Double cantidad;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}