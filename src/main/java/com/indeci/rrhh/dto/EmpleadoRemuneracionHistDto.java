package com.indeci.rrhh.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fila del historial remunerativo (F2) — espejo de EmpleadoRemuneracionHist. */
public record EmpleadoRemuneracionHistDto(
        Long id,
        LocalDate vigenciaDesde,
        LocalDate vigenciaHasta,
        Double montoBase,
        Double remuneracionTotal,
        String tipoCambio,
        String documentoSustento,
        String fuente,
        String estado,
        String observacion,
        String createdBy,
        LocalDateTime createdAt) {
}
