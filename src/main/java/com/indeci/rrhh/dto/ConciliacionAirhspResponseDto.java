package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Spec 010 / M13 — Response de una conciliación AIRHSP.
 */
@Data
public class ConciliacionAirhspResponseDto {

    private Long id;
    private Long empleadoId;
    /** Código del registro del empleado en AIRHSP (INDECI_EMPLEADO.REGISTRO_AIRHSP). */
    private String registroAirhsp;
    private Long movimientoPlanillaId;
    private Long periodoPlanillaId;
    private Double montoSistema;
    private Double montoAirhsp;
    private Double diferencia;
    private String estado;
    private String justificacion;
    private Long usuarioRevisa;
    private LocalDate fechaRevision;
}
