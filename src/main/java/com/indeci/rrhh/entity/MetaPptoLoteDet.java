package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * V010_77 — Detalle de validación por empleado dentro de un proceso masivo.
 * Permite vista previa, corrección de excepciones y auditoría antes de publicar.
 */
@Entity
@Table(name = "INDECI_META_PPTO_LOTE_DET", schema = "GESTIONRRHH")
@Data
public class MetaPptoLoteDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → INDECI_META_PPTO_LOTE.ID */
    @Column(name = "LOTE_ID", nullable = false)
    private Long loteId;

    /** FK → INDECI_EMPLEADO.ID */
    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** FK → INDECI_META_PPTO_CAT.ID (meta del año origen) */
    @Column(name = "META_ORIGEN_ID")
    private Long metaOrigenId;

    /** FK → INDECI_META_PPTO_CAT.ID (meta propuesta para el año destino) */
    @Column(name = "META_DESTINO_ID")
    private Long metaDestinoId;

    /** FK → INDECI_EMP_META_ANUAL.ID (asignación creada si OK) */
    @Column(name = "EMP_META_ANUAL_ID")
    private Long empMetaAnualId;

    /**
     * OK | OBSERVADO | SIN_EQUIVALENCIA | META_DESTINO_INACTIVA |
     * EMPLEADO_INACTIVO | DUPLICADO | ERROR
     */
    @Column(name = "ESTADO_VALIDACION", nullable = false, length = 40)
    private String estadoValidacion = "OK";

    @Column(name = "MENSAJE_VALIDACION", length = 1000)
    private String mensajeValidacion;

    @Column(name = "ACCION_SUGERIDA", length = 500)
    private String accionSugerida;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
