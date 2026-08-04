package com.indeci.rrhh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.indeci.rrhh.entity.Vacacion;

/**
 * Trazabilidad Visual — una fila de goce vacacional (INDECI_VACACIONES) con quién y cuándo la
 * registró. Alimenta la pestaña "Goces Directos" del modal Historial y el resumen de Override
 * en "Ver Detalle" (V012_55).
 */
public record GoceRegistradoDto(
        Long id,
        LocalDateTime fechaRegistro,
        String usuarioRegistro,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        Double dias,
        String tipoGoce,
        String origen,
        Integer esAdelanto,
        String documentoSustento,
        String motivoExcepcion,
        String estado) {

    public static GoceRegistradoDto from(Vacacion v) {
        return new GoceRegistradoDto(
                v.getId(), v.getCreatedAt(), v.getUsuarioRegistro(),
                v.getPeriodoDesde(), v.getPeriodoHasta(), v.getDias(),
                v.getTipoGoce(), v.getOrigen(), v.getEsAdelanto(),
                v.getDocumentoSustento(), v.getMotivoExcepcion(), v.getEstado());
    }
}
