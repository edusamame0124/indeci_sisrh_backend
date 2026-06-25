package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Response del catálogo de conceptos de planilla.
 *
 * <p>Campos básicos (Spec 009) — id/codigo/nombre/tipo/naturaleza/activo.</p>
 *
 * <p>Spec 013 / C1 — campos MEF/SISPER para el modal de Asignar Descuento.</p>
 *
 * <p>F3.2 — campos extendidos del catálogo enriquecido (Spec 010 §6.1, V010_27
 * y V010_42): códigos externos PLAME/MCPP, afectaciones (5ta/AFP/ESSALUD),
 * banderas MUC/CUC, régimen aplicable (CSV F1.5b), vigencia y prorrateabilidad.
 * Permite que la UI muestre chips visuales por afectación sin queries adicionales.</p>
 */
@Data
public class ConceptoPlanillaResponseDto {

    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String naturaleza;
    private Integer activo;

    // Spec 013 / C1 — campos MEF expuestos para el dropdown del modal
    // "Asignar Descuento / Ajuste Manual" (agrupación y filtro por tipo/SISPER).
    private String codigoMef;
    private String codigoSisper;
    private String tipoConcepto;

    /** §13 — "Tipo de Concepto" funcional (SISPER); el motor sigue usando tipoConcepto. */
    private String tipoConceptoInterno;

    // F3.2 — códigos externos para PLAME y MCPP (V010_27 / B3).
    private String codigoPlameSunat;
    private String codigoMcpp;

    // F3.2 — afectaciones tributarias y previsionales (S/N).
    private String afectoIr5ta;
    private String afectoAportePens;
    private String afectoEssalud;

    // F3.2 — banderas LEY-07 MUC vs CUC.
    private String esMuc;
    private String esCuc;

    // F3.2 — régimen(es) aplicable(s). Formato CSV permitido (F1.5b):
    // "276" | "728" | "1057" | "SERVIR" | "TODOS" | "728,1057" | ...
    private String regimenAplicable;

    private LocalDate fechaVigIni;
    private LocalDate fechaVigFin;

    // F1.5b — el motor v3 prorratea el monto por días laborados si "S".
    private String esProrrateable;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P1 (V010_97) — ciclo de vida + RTPS
    // ============================================================

    /** BORRADOR | EN_REVISION | ACTIVO | CERRADO | ANULADO. */
    private String estado;

    /** FK al catálogo RTPS (PDT 601). */
    private String rtpsCodigo;

    /** Descripción RTPS resuelta para display (evita un join en la UI). */
    private String rtpsDescripcion;

    /** Código de tributo SUNAT (ej. 3042) — antes solo en request. */
    private String codigoTributoSunat;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P3 (V010_99) — versionado por vigencia
    // ============================================================

    /** N.º de versión por CÓDIGO (display). DEFAULT 1. */
    private Integer version;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA §14 / P4 (V010_101) — Modo de cálculo
    // ============================================================

    /**
     * Modo de cálculo (metadata): MONTO_FIJO | MONTO_INDIVIDUAL | PORCENTAJE |
     * RESULTADO_MOTOR | IMPORTACION. DEFAULT 'RESULTADO_MOTOR'. El motor NO se
     * ramifica por este campo.
     */
    private String modoCalculo;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA §15 / Fase A (V010_102) — planillas asociadas
    // ============================================================

    /**
     * Códigos de los tipos de planilla asociados al concepto (M:N). Vacío si el
     * concepto aún no declara ninguno (conceptos legacy previos a Fase A).
     */
    private List<String> planillaTipos;

    // ============================================================
    // SPEC_HOMOLOGACION_MGRH §C.2 (V010_103) — homologación MGRH/MEF
    // ============================================================

    /** FK al Catálogo MGRH/MEF homologado. {@code null} si Pendiente. */
    private Long catalogoConceptoMgrhId;

    /** Observación interna de la homologación MGRH (opcional). */
    private String observacionHomologacionMgrh;

    /** ¿Se incluye en planilla de pago? 'S' (≥1 planilla) / 'N' (solo config/cálculo/control). */
    private String incluyeEnPlanilla;

    /** Estado derivado: "HOMOLOGADO" si la FK no es null; si no, "PENDIENTE". */
    private String estadoHomologacionMgrh;

    /**
     * Resumen read-only del concepto MGRH homologado (tipo/código/descripción de la
     * norma) para mostrar el chip/detalle sin una segunda llamada. {@code null} si
     * el concepto está Pendiente.
     */
    private MgrhResumen mgrhResumen;

    /** Resumen mínimo del concepto MGRH homologado (display). */
    @Data
    public static class MgrhResumen {
        private Long id;
        private String tipo;
        private String codigoConceptoMgrh;
        private String descripcionNorma;
    }
}
