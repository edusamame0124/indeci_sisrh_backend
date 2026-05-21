package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Spec 010 / V010_10 — Fila de abono bancario para carga + ticket MCPP (M14).
 */
@Entity
@Table(name = "INDECI_ABONO_BANCO", schema = "GESTIONRRHH")
@Data
public class AbonoBanco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MOVIMIENTO_PLANILLA_ID", nullable = false)
    private Long movimientoPlanillaId;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** BCP | BBVA | SCOTIABANK | BANBIF | INTERBANK | B.NACION. */
    @Column(name = "BANCO", nullable = false)
    private String banco;

    @Column(name = "NRO_CUENTA")
    private String nroCuenta;

    @Column(name = "CCI")
    private String cci;

    /** Meta presupuestal del empleado. */
    @Column(name = "META")
    private String meta;

    @Column(name = "MONTO_NETO", nullable = false)
    private Double montoNeto;

    /** PENDIENTE | PROCESADO | RECHAZADO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    /** Ticket MCPP ingresado por Tesorería al confirmar el pago. */
    @Column(name = "NRO_TICKET_MCPP")
    private String nroTicketMcpp;

    @Column(name = "FECHA_PROCESADO")
    private LocalDate fechaProcesado;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
