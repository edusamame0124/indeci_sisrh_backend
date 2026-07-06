package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Historial remunerativo del vínculo (V012_05, F2). Cada fila es una remuneración
 * con vigencia; el motor resuelve la base según el período que procesa. No
 * sobrescribe: una renovación/adenda/incremento crea una fila nueva.
 */
@Entity
@Table(name = "INDECI_EMPLEADO_REMUNERACION_HIST", schema = "GESTIONRRHH")
@Data
public class EmpleadoRemuneracionHist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_PLANILLA_ID")
    private Long empleadoPlanillaId;

    @Column(name = "VIGENCIA_DESDE")
    private LocalDate vigenciaDesde;

    /** Null = vigencia abierta. */
    @Column(name = "VIGENCIA_HASTA")
    private LocalDate vigenciaHasta;

    /** Monto base de contrato. */
    @Column(name = "MONTO_BASE")
    private Double montoBase;

    /** Remuneración total mensual (la BASE que usa el motor). */
    @Column(name = "REMUNERACION_TOTAL")
    private Double remuneracionTotal;

    /** CONTRATO_INICIAL / ADENDA / INCREMENTO / REDUCCION / RENOVACION / MIGRACION_LEGACY. */
    @Column(name = "TIPO_CAMBIO")
    private String tipoCambio;

    @Column(name = "DOCUMENTO_SUSTENTO")
    private String documentoSustento;

    /** MANUAL | MIGRACION_LEGACY. */
    @Column(name = "FUENTE")
    private String fuente;

    /** BORRADOR | APROBADO | ANULADO. Solo APROBADO alimenta el motor. */
    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
