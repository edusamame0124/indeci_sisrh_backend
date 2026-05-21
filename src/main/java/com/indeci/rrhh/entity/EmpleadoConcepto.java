package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_EMPLEADO_CONCEPTO",
        schema = "GESTIONRRHH"
)
@Data
public class EmpleadoConcepto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "MONTO")
    private Double monto;

    @Column(name = "PORCENTAJE")
    private Double porcentaje;

    @Column(name = "FORMULA")
    private String formula;

    /** Spec 013/C1 — mes/año desde el que aplica el concepto. */
    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    /** Spec 013/C1 — mes/año hasta el que aplica. NULL = indefinido. */
    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}