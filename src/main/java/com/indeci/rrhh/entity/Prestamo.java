package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Préstamo del empleado (SPEC §12.2 PANTALLA-08).
 * El saldo pendiente se deriva: MONTO_TOTAL − CUOTAS_PAGADAS × CUOTA_MENSUAL.
 */
@Entity
@Table(name = "INDECI_PRESTAMO", schema = "GESTIONRRHH")
@Data
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "MONTO_TOTAL")
    private Double montoTotal;

    @Column(name = "NUMERO_CUOTAS")
    private Integer numeroCuotas;

    @Column(name = "CUOTA_MENSUAL")
    private Double cuotaMensual;

    @Column(name = "CUOTAS_PAGADAS")
    private Integer cuotasPagadas;

    /** ACTIVO | CANCELADO. */
    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
