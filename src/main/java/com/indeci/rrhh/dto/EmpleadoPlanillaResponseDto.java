package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPlanillaResponseDto {

    private Long id;

    // =====================================
    // CONFIGURACION BASE
    // =====================================

    private Double sueldoBasico;

    private String codigoAirhsp;

    private Double montoContrato;

    private Double movilidad;

    private Double alimentacion;

    // =====================================
    // ASIGNACION FAMILIAR
    // =====================================

    private Integer tieneAsignacionFamiliar;

    private Integer numHijos;

    // =====================================
    // DESCUENTOS FIJOS
    // =====================================

    private Double descuentoBanco;

    private Double descuentoInstitucion;

    // =====================================
    // CONFIGURACIÓN LABORAL (mejora 2026-06-03 — para prefill en edición)
    // =====================================

    private Long regimenLaboralId;

    private Long tipoContratoId;

    private Long condicionLaboralId;

    private Long modalidadCasId;

    /** Gate de Modalidad de Teletrabajo (Ley N° 31572, V012_28). */
    private Integer esTeletrabajador;

    /** Ley 30057 (V012_07). */
    private String grupoServidorCivil;

    private Integer esConfianza;

    private Long tipoPersonaMefId;

    private String registroPlazaAirhsp;

    // Etiquetas resueltas para mostrar en el listado (no requieren catálogos en UI).
    private String regimenLaboral;   // código (ej. CAS, 728)

    private String tipoContrato;     // nombre

    private String condicionLaboral; // nombre

    private String modalidadCas;     // nombre

    // =====================================
    // FECHAS
    // =====================================

    private java.time.LocalDate fechaInicioContrato;

    /** SPEC_VACACIONES F9.1 — override de jornada (null=hereda régimen; 6=operativo COEN/DDI). */
    private Integer diasSemanaOperativo;

    /** Fecha fin contractual (término previsto del vínculo). */
    private java.time.LocalDate fechaFin;

    // =====================================
    // CESE (V012_04) — hechos que registra RR.HH.
    // =====================================

    private java.time.LocalDate fechaCese;

    private String motivoCese;

    private String documentoCese;

    // Sustento de origen del vínculo (V012_08).
    private String documentoOrigenTipo;

    private String documentoOrigenNumero;

    private java.time.LocalDate documentoOrigenFecha;

    // =====================================
    // ESTADO
    // =====================================

    private Integer activo;

    /** Estado DERIVADO del vínculo (no editable): PROGRAMADO / VIGENTE /
     *  VENCIDO_PENDIENTE_DE_REGULARIZACION / CESADO / ANULADO. */
    private String estadoVinculo;

    /** true si el cese es formal y completo (habilita generar LBS). */
    private Boolean habilitaLbs;

    /** Aviso normativo del vínculo (Excel import "PLAZO MAXIMO"); se muestra en Config. Remunerativa. */
    private String plazoMaximo;
}