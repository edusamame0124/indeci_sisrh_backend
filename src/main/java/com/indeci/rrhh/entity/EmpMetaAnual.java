package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V010_77 — Asignación anual de meta presupuestal a un trabajador.
 * UK funcional en BD: 1 sola asignación no-ANULADA por empleado y año fiscal.
 * Al cerrar período: BLOQUEADO_POR_PLANILLA = 1 (sin modificaciones posteriores).
 * La planilla lee filtrando ESTADO = 'PUBLICADO'.
 */
@Entity
@Table(name = "INDECI_EMP_META_ANUAL", schema = "GESTIONRRHH")
@Data
public class EmpMetaAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    /** FK → INDECI_META_PPTO_CAT.ID */
    @Column(name = "META_PPTO_CAT_ID", nullable = false)
    private Long metaPptoCatId;

    @Column(name = "VIGENCIA_INICIO", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "VIGENCIA_FIN")
    private LocalDate vigenciaFin;

    /** BORRADOR | VALIDADO | PUBLICADO | CERRADO | ANULADO | OBSERVADO */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado = "BORRADOR";

    /** MANUAL | COPIA_ANIO_ANTERIOR | EQUIVALENCIA | IMPORTACION_EXCEL | REGULARIZACION */
    @Column(name = "ORIGEN", nullable = false, length = 40)
    private String origen = "MANUAL";

    /** FK → INDECI_META_PPTO_LOTE.ID (nullable) */
    @Column(name = "LOTE_ID")
    private Long loteId;

    /** 0 = editable; 1 = bloqueado por planilla cerrada */
    @Column(name = "BLOQUEADO_POR_PLANILLA", nullable = false)
    private Integer bloqueadoPorPlanilla = 0;

    @Column(name = "OBSERVACION", length = 1000)
    private String observacion;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;

    @Column(name = "MOTIVO_ANULACION", length = 1000)
    private String motivoAnulacion;

    // ── Snapshot de la meta presupuestal (copiados desde INDECI_META_PPTO_CAT al asignar) ──

    @Column(name = "CENTRO_COSTO", length = 1000)
    private String centroCosto;

    @Column(name = "CATEGORIA_PRESUPUESTAL", length = 1000)
    private String categoriaPresupuestal;

    @Column(name = "PRODUCTO", length = 1000)
    private String producto;

    @Column(name = "ACTIVIDAD", length = 1000)
    private String actividad;

    @Column(name = "FINALIDAD", length = 1000)
    private String finalidad;

    // ── Snapshot del empleado (copiados desde INDECI_PERSONA al asignar) ──

    @Column(name = "DNI", length = 8)
    private String dni;

    @Column(name = "NOMBRES", length = 200)
    private String nombres;
}
