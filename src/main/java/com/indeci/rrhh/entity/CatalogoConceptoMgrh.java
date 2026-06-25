package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SPEC_HOMOLOGACION_MGRH §C.1 — Catálogo Único de Conceptos MGRH/MEF (DGGRP).
 *
 * <p>Tabla maestra de REFERENCIA (no motor, no cálculo). Versiones anuales: la
 * clave de negocio es {@code TIPO + CODIGO_CONCEPTO_MGRH + ANIO_CATALOGO}. El
 * {@code CODIGO_CONCEPTO_MGRH} es TEXTO (preserva ceros, ej. {@code 0001}). La PK
 * técnica es {@code ID} IDENTITY (naming INDECI_*, sin SEQUENCE).</p>
 *
 * <p>{@code VIGENTE='S'} marca la versión anual más reciente (consulta por defecto);
 * {@code SELECCIONABLE='N'} excluye {@code GASTOS POR ENCARGO} del buscador ordinario.
 * {@code FECHA_VIGENCIA_TEXTO} conserva el texto crudo del Excel; {@code
 * FECHA_VIGENCIA_DATE} es el derivado nullable (D2).</p>
 *
 * <p>Seed: {@code V010_104__seed_catalogo_concepto_mgrh.sql} (año 2026, 1269 filas).</p>
 */
@Entity
@Table(name = "INDECI_CATALOGO_CONCEPTO_MGRH", schema = "GESTIONRRHH")
@Data
public class CatalogoConceptoMgrh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** INGRESOS | APORTES | EGRESOS | GASTOS POR ENCARGO. */
    @Column(name = "TIPO")
    private String tipo;

    /** Código MGRH (TEXTO, preserva ceros). Ej: 0001, 5000. */
    @Column(name = "CODIGO_CONCEPTO_MGRH")
    private String codigoConceptoMgrh;

    @Column(name = "DESCRIPCION_NORMA")
    private String descripcionNorma;

    @Column(name = "DETALLE_NORMA")
    private String detalleNorma;

    /** D2 — texto crudo del Excel (siempre conservado). */
    @Column(name = "FECHA_VIGENCIA_TEXTO")
    private String fechaVigenciaTexto;

    /** D2 — derivado nullable (solo dd/mm/yyyy parseable). */
    @Column(name = "FECHA_VIGENCIA_DATE")
    private LocalDate fechaVigenciaDate;

    /** SI | NO. */
    @Column(name = "IMPONIBLE")
    private String imponible;

    @Column(name = "DESCRIPCION_TIPO_CONCEPTO")
    private String descripcionTipoConcepto;

    /** Permanente | Ocasional. */
    @Column(name = "TIPO_NORMA")
    private String tipoNorma;

    /** Valor oficial (Activo). */
    @Column(name = "ESTADO")
    private String estado;

    /** 'S' seleccionable; 'N' GASTOS POR ENCARGO (no seleccionable en flujo ordinario). */
    @Column(name = "SELECCIONABLE")
    private String seleccionable;

    /** D6 — versión anual (2026). */
    @Column(name = "ANIO_CATALOGO")
    private Integer anioCatalogo;

    /** D6 — 'S' = versión anual más reciente. */
    @Column(name = "VIGENTE")
    private String vigente;

    /** D6 — corte de la fuente (ej. Conceptos2026.xls). */
    @Column(name = "FUENTE_CATALOGO")
    private String fuenteCatalogo;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_REGISTRO")
    private LocalDateTime fechaRegistro;

    @Column(name = "FECHA_MODIFICACION")
    private LocalDateTime fechaModificacion;
}
