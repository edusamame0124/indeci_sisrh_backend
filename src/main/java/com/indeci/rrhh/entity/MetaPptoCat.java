package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V010_77 — Catálogo anual único de metas presupuestales.
 * Una fila por combinación única (ANIO + META_CODIGO + CENTRO_COSTO + CATEGORIA +
 * PRODUCTO + ACTIVIDAD + FINALIDAD). La unicidad real la garantiza META_HASH (SHA-256).
 * No usar para asignación por empleado — ver {@link EmpMetaAnual}.
 */
@Entity
@Table(name = "INDECI_META_PPTO_CAT", schema = "GESTIONRRHH")
@Data
public class MetaPptoCat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    @Column(name = "META_CODIGO", nullable = false, length = 30)
    private String metaCodigo;

    @Column(name = "CENTRO_COSTO", nullable = false, length = 500)
    private String centroCosto;

    @Column(name = "CATEGORIA_PRESUPUESTAL", nullable = false, length = 500)
    private String categoriaPresupuestal;

    @Column(name = "PRODUCTO", nullable = false, length = 500)
    private String producto;

    @Column(name = "ACTIVIDAD", nullable = false, length = 500)
    private String actividad;

    @Column(name = "FINALIDAD", nullable = false, length = 500)
    private String finalidad;

    /** Columna VIRTUAL en Oracle (SHA-256 de los 7 campos). Nunca persistir. */
    @Column(name = "META_HASH", insertable = false, updatable = false, length = 64)
    private String metaHash;

    /** BORRADOR | VALIDADO | PUBLICADO | CERRADO | ANULADO */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado = "BORRADOR";

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    @Column(name = "FUENTE", length = 300)
    private String fuente;

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
}
