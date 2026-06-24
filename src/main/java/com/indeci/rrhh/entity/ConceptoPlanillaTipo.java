package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — asociación M:N entre un concepto
 * ({@link ConceptoPlanilla}) y un tipo de planilla ({@link PlanillaTipo}).
 *
 * <p>Un concepto declara ≥1 tipo de planilla (regla validada en
 * {@code ConceptoPlanillaService}). PK transaccional ({@code ID} IDENTITY); la
 * unicidad del par {@code (CONCEPTO_PLANILLA_ID, PLANILLA_TIPO_CODIGO)} la garantiza
 * la BD ({@code INDECI_CONC_PLA_TIPO_UK}).</p>
 *
 * <p>Metadata: en Fase A el motor NO filtra la generación por esta asociación.</p>
 */
@Entity
@Table(name = "INDECI_CONCEPTO_PLANILLA_TIPO", schema = "GESTIONRRHH")
@Data
public class ConceptoPlanillaTipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "PLANILLA_TIPO_CODIGO")
    private String planillaTipoCodigo;
}
