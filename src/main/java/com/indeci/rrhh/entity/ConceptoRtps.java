package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 — Catálogo "Tipo Concepto RTPS" del PDT 601 (INDECI).
 *
 * <p>PK natural ({@code CODIGO}, sin {@code @GeneratedValue}) como
 * {@code INDECI_CAT_SUSPENSION_SUNAT}: el código preserva ceros a la izquierda
 * (0703, 0704). {@code ES_GRUPO='S'} marca cabeceras de grupo que NO son
 * seleccionables como RTPS de un concepto (solo agrupan en la UI).</p>
 *
 * <p>Seed: {@code V010_98__seed_concepto_rtps_pdt601.sql}.</p>
 */
@Entity
@Table(name = "INDECI_CONCEPTO_RTPS", schema = "GESTIONRRHH")
@Data
public class ConceptoRtps {

    /** Código RTPS/PDT 601 (PK natural, preserva ceros). Ej: 0703, 0915, 2046. */
    @Id
    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    /** Código del grupo (0100/0300/0400/0700/0900/1000/2000). */
    @Column(name = "GRUPO_CODIGO")
    private String grupoCodigo;

    @Column(name = "GRUPO_DESCRIPCION")
    private String grupoDescripcion;

    /** 'S' = cabecera de grupo (no seleccionable); 'N' = item seleccionable. */
    @Column(name = "ES_GRUPO")
    private String esGrupo;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FUENTE")
    private String fuente;

    @Column(name = "FECHA_CORTE_FUENTE")
    private LocalDate fechaCorteFuente;
}
