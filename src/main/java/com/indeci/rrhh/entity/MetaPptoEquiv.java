package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * V010_77 — Equivalencia de meta presupuestal entre años fiscales.
 * UK funcional en BD: 1 sola equivalencia ACTIVA/no-ANULADA
 * por (META_ORIGEN_ID, ANIO_DESTINO).
 */
@Entity
@Table(name = "INDECI_META_PPTO_EQUIV", schema = "GESTIONRRHH")
@Data
public class MetaPptoEquiv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ANIO_ORIGEN", nullable = false)
    private Integer anioOrigen;

    /** FK → INDECI_META_PPTO_CAT.ID */
    @Column(name = "META_ORIGEN_ID", nullable = false)
    private Long metaOrigenId;

    @Column(name = "ANIO_DESTINO", nullable = false)
    private Integer anioDestino;

    /** FK → INDECI_META_PPTO_CAT.ID */
    @Column(name = "META_DESTINO_ID", nullable = false)
    private Long metaDestinoId;

    /** BORRADOR | VALIDADO | PUBLICADO | ANULADO */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado = "BORRADOR";

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

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

    /** AUTOMATICO = detectado por estructura; MANUAL = ingresado por analista. V010_78. */
    @Column(name = "TIPO_ORIGEN", length = 20)
    private String tipoOrigen = "MANUAL";
}
