package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

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
}
