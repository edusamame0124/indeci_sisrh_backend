package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F2.1 — Evento del período del empleado (maternidad, enfermedad, licencias,
 * lactancia, paternidad, cese, reintegro, etc.).
 *
 * <p>Consumido por dos pasos del Motor v3:</p>
 *
 * <ul>
 *   <li>{@code calcularDiasLaborados} (F2.3): suma {@code diasAfectos} de
 *       los eventos vigentes del período cuyo {@code TipoEvento.afectaDiasLaborados='S'}.</li>
 *   <li>{@code SubsidioCalculadorService} (F2.4): para eventos con
 *       {@code TipoEvento.generaSubsidio='S'} calcula subsidio EsSalud
 *       (promedio 12 meses) + diferencia INDECI.</li>
 * </ul>
 *
 * <p>{@code diasAfectos} es derivado por default ({@code fechaFin - fechaInicio + 1})
 * pero el service permite sobrescribirlo para días no laborables o casos
 * especiales (lactancia parcial donde solo cuentan ciertas horas).</p>
 *
 * <p>{@code sustentoLegajoDocId} apunta opcionalmente a un documento en
 * {@code INDECI_LEGAJO_DOCUMENTO}. Si {@code TipoEvento.requiereAdjunto='S'},
 * el service exige que esté presente al crear el evento (F2.6).</p>
 */
@Entity
@Table(name = "INDECI_EMPLEADO_EVENTO")
@Data
public class EmpleadoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "TIPO_EVENTO_ID", nullable = false)
    private Long tipoEventoId;

    /**
     * Carga lazy del catálogo {@link TipoEvento} — permite al motor leer
     * los flags ({@code afectaDiasLaborados}, etc.) sin un query separado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TIPO_EVENTO_ID", insertable = false, updatable = false)
    private TipoEvento tipoEvento;

    /** Formato YYYYMM, opcional — facilita las queries del motor por período. */
    @Column(name = "PERIODO", length = 6)
    private String periodo;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    /** Días afectos al cálculo. Si null → derivar de fechaFin-fechaInicio+1. */
    @Column(name = "DIAS_AFECTOS")
    private Integer diasAfectos;

    /** FK opcional a {@code INDECI_LEGAJO_DOCUMENTO} (sustento documental). */
    @Column(name = "SUSTENTO_LEGAJO_DOC_ID")
    private Long sustentoLegajoDocId;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    /** REGISTRADO | VALIDADO | RECHAZADO. */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;

    /** P0 maternidad — 98 o 128 días naturales. Null para otros tipos. */
    @Column(name = "DURACION_LEGAL")
    private Integer duracionLegal;

    @Column(name = "MOTIVO_EXTENSION", length = 40)
    private String motivoExtension;

    @Column(name = "FECHA_PROBABLE_PARTO")
    private LocalDate fechaProbableParto;

    @Column(name = "DIFIERE_PRENATAL_POSTNATAL", length = 20)
    private String difierePrenatalPostnatal;

    @Column(name = "TIPO_DOCUMENTO", length = 30)
    private String tipoDocumento;

    @Column(name = "NRO_CITT", length = 50)
    private String nroCitt;

    @Column(name = "FECHA_EMISION_DOC")
    private LocalDate fechaEmisionDoc;
}
