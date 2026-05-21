package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Cuerpo POST de carga de asistencia (M04 / SPEC §12.2 PANTALLA-02).
 * El servicio hace UPSERT por (empleadoId, periodo) y recalcula los totales.
 */
@Data
public class AsistenciaGuardarDto {

    private Long empleadoId;

    private String periodo;

    /** Remuneración mensual base del descuento (D.Leg. 276 Art. 24). */
    private Double remuneracionBase;

    private String observacion;

    /** BORRADOR (default) | VALIDADA. */
    private String estado;

    private List<AsistenciaDiaDto> dias;
}
