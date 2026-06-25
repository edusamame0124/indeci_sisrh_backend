package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Request del catálogo de conceptos de planilla (crear/editar).
 *
 * <p>SPEC_CONCEPTOS_PLANILLA P1 (§10.2) — se extiende de 4 a TODOS los campos
 * editables del concepto para que RR. HH. configure la metadata regulatoria desde
 * la UI (antes solo se sembraba por migración).</p>
 *
 * <p>{@code estado} NO es editable libremente: se fuerza a {@code BORRADOR} al crear
 * y solo cambia por los endpoints de transición (enviar-revisión/activar/cerrar/anular).</p>
 */
@Data
public class ConceptoPlanillaDto {

    // --- Datos básicos ---
    /**
     * §13 — OPCIONAL: si llega null/blank el server genera un correlativo
     * {@code CONC-####} desde {@code INDECI_SEQ_CONCEPTO_COD}. Si llega (conceptos
     * técnicos/seed), se respeta tal cual.
     */
    private String codigo;
    private String nombre;
    private String tipo;        // legacy INGRESO|DESCUENTO|APORTE (compat Spec 009)
    private String naturaleza;

    // --- Clasificación motor ---
    private String tipoConcepto; // REMUNERATIVO|NO_REMUNERATIVO|DESCUENTO|APORTE_TRABAJADOR|APORTE_EMPLEADOR

    /**
     * §13 — "Tipo de Concepto" funcional (SISPER). FK a INDECI_TIPO_CONCEPTO_INTERNO.
     * Si llega, el server DERIVA {@code tipoConcepto} (motor) de su CLASIFICACION_MOTOR.
     */
    private String tipoConceptoInterno;

    // --- Clasificación externa ---
    private String codigoMef;
    private String codigoSisper;
    private String codigoPlameSunat;
    private String codigoMcpp;
    private String codigoTributoSunat;
    private String rtpsCodigo;   // FK INDECI_CONCEPTO_RTPS(CODIGO)

    // --- Afectaciones (S/N) ---
    private String afectoIr5ta;
    private String afectoAportePens;
    private String afectoEssalud;
    private String esMuc;
    private String esCuc;

    // --- Aplicabilidad ---
    private String regimenAplicable; // CSV: 276|728|1057|SERVIR|TODOS
    private LocalDate fechaVigIni;
    private LocalDate fechaVigFin;

    // --- Regla de cálculo ---
    private String esProrrateable;   // S/N

    /**
     * §14 / P4 — Modo de cálculo (metadata). Opcional: si llega null/blank el
     * server lo persiste como 'RESULTADO_MOTOR'. Valores: MONTO_FIJO |
     * MONTO_INDIVIDUAL | PORCENTAJE | RESULTADO_MOTOR | IMPORTACION.
     * El motor NO se ramifica por este campo.
     */
    private String modoCalculo;

    /**
     * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — códigos de los tipos de planilla
     * asociados al concepto ({@code INDECI_PLANILLA_TIPO.CODIGO}). El concepto debe
     * declarar AL MENOS UNO; un payload vacío/nulo se rechaza con NegocioException.
     */
    private List<String> planillaTipos;

    /**
     * SPEC_HOMOLOGACION_MGRH §C.2 — homologación con el Catálogo MGRH/MEF.
     * OPCIONAL (nullable): {@code null} = Pendiente de homologación; con valor =
     * Homologado. No obligatorio, no bloquea crear/editar/activar.
     */
    private Long catalogoConceptoMgrhId;

    /** Observación interna de la homologación MGRH (opcional). */
    private String observacionHomologacionMgrh;

    /** ¿Se incluye en planilla de pago? 'S' (≥1 planilla) / 'N' (solo config/cálculo/control). */
    private String incluyeEnPlanilla;
}
