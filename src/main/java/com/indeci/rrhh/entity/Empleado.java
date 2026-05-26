package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO", schema = "GESTIONRRHH")
@Data
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERSONA_ID")
    private Long personaId;

    @Column(name = "CODIGO_INTERNO")
    private String codigoInterno;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "PROFESION_ID")
    private Long profesionId;

    @Column(name = "GRADO_ACADEMICO_ID")
    private Long gradoAcademicoId;

    @Column(name = "CONADIS_CODIGO")
    private String conadisCodigo;
    
    @Column(name = "TIPO_PERSONAL_ID")
    private Long tipoPersonalId;
    
    @Column(name = "CODIGO_SISPER")
    private String codigoSisper;

    // ============================================================
    // Spec 010 / V010_07 — campos permanentes del trabajador
    // ============================================================

    /** S/N — trabajador con EPS: habilita split ESSALUD 6.75% / 2.25% (SPEC §5.5). */
    @Column(name = "HAS_EPS", nullable = false, length = 1)
    private String hasEps = "N";

    /** DNI del titular cuando el empleado está en encargatura (alimenta EmpleadoEncargatura). */
    @Column(name = "DNI_REEMPLAZADO")
    private String dniReemplazado;

    /** Código MEF del registro del empleado en AIRHSP (base de la conciliación M13). */
    @Column(name = "REGISTRO_AIRHSP")
    private String registroAirhsp;

    /** Monto de remuneración vigente del empleado en AIRHSP (MEF) — PASO 16 / M13. */
    @Column(name = "AIRHSP_MONTO")
    private Double airhspMonto;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "PERSONA_ID",
            insertable = false,
            updatable = false)
    private Persona persona;

}