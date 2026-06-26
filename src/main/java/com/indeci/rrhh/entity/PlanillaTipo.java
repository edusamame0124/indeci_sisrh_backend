package com.indeci.rrhh.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — Catálogo administrable de tipos de
 * planilla / cédula (CAS, CAS TEMPORAL, CAS ADICIONAL; la entidad agrega más).
 *
 * <p>PK natural ({@code CODIGO}, sin {@code @GeneratedValue}) como los demás
 * catálogos del módulo de conceptos. Es metadata: en Fase A el motor NO filtra
 * la generación por tipo de planilla (eso es Fase B).</p>
 *
 * <p>Seed inicial: {@code V010_102__planilla_tipo_y_asociacion.sql}.</p>
 */
@Entity
@Table(name = "INDECI_PLANILLA_TIPO", schema = "GESTIONRRHH")
@Data
public class PlanillaTipo {

    /** Código del tipo de planilla (PK natural). Ej: CAS, CAS_TEMP, CAS_ADIC. */
    @Id
    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "CREADO_POR", updatable = false)
    private String creadoPor;

    @Column(name = "CREADO_EN", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR")
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
