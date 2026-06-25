package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — Catálogo "Tipo de Concepto" (taxonomía funcional SISPER).
 *
 * <p>PK natural ({@code CODIGO}, sin {@code @GeneratedValue}) como
 * {@link ConceptoRtps}. Cada fila lleva su {@code CLASIFICACION_MOTOR}: de ahí
 * se DERIVA el {@code TIPO_CONCEPTO} del motor (muchos-a-uno, data-driven). El
 * motor sigue leyendo {@code TIPO_CONCEPTO}; este catálogo es solo la dimensión
 * funcional que conoce RR. HH.</p>
 *
 * <p>Seed: {@code V010_100__tipo_concepto_interno_y_codigo_auto.sql}.</p>
 */
@Entity
@Table(name = "INDECI_TIPO_CONCEPTO_INTERNO", schema = "GESTIONRRHH")
@Data
public class ConceptoTipoInterno {

    /** Código SISPER (PK natural). Ej: REM_FIJA, DESC_FIJO, APORTE_TRAB. */
    @Id
    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;

    /**
     * Clasificación del motor derivada: REMUNERATIVO | NO_REMUNERATIVO | DESCUENTO
     * | APORTE_TRABAJADOR | APORTE_EMPLEADOR. Es el valor que el service copia a
     * {@code ConceptoPlanilla.tipoConcepto} (el motor no cambia).
     */
    @Column(name = "CLASIFICACION_MOTOR")
    private String clasificacionMotor;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "ACTIVO")
    private Integer activo;
}
