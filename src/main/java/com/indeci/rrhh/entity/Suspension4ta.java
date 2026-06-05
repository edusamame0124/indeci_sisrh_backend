package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * FASE 1 / V010_48 — Constancia SUNAT de suspensión de retención de 4ta
 * categoría (régimen CAS). Vigencia por fecha.
 *
 * <p>Alimenta {@code GeneradorPlanillaService.calcular4taCategoriaCAS}: si hay
 * una constancia ACTIVA vigente en la fecha de devengue, la retención IR 4ta
 * del empleado CAS es 0.</p>
 *
 * <p><b>NO confundir</b> con {@link Suspension} (INDECI_SUSPENSION), que es
 * suspensión laboral/licencia (Tabla 21 SUNAT, fuente del .snl de PLAME). Esta
 * es de naturaleza <em>tributaria</em>.</p>
 */
@Entity
@Table(name = "INDECI_SUSPENSION_4TA", schema = "GESTIONRRHH")
@Data
public class Suspension4ta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** N.° de operación/constancia SUNAT (Excel CAS col AH). */
    @Column(name = "NRO_CONSTANCIA")
    private String nroConstancia;

    @Column(name = "FECHA_EMISION")
    private LocalDate fechaEmision;

    @Column(name = "FECHA_VIG_INI", nullable = false)
    private LocalDate fechaVigIni;

    /** NULL = vigencia indefinida (se recomienda setear: SUNAT suele renovarse anual). */
    @Column(name = "FECHA_VIG_FIN")
    private LocalDate fechaVigFin;

    /** ACTIVO | ANULADO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "OBSERVACION")
    private String observacion;

    /** FK opcional al PDF de la constancia en INDECI_LEGAJO_DOCUMENTO. */
    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private String updatedBy;
}
