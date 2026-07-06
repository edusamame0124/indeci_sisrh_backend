package com.indeci.rrhh.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Alta de un cambio remunerativo con vigencia (F2). */
public record RemuneracionCambioInput(
        @NotNull(message = "La vigencia desde es obligatoria")
        LocalDate vigenciaDesde,

        Double montoBase,

        @NotNull(message = "La remuneración total es obligatoria")
        @DecimalMin(value = "0.01", message = "La remuneración total debe ser mayor a cero")
        Double remuneracionTotal,

        /** CONTRATO_INICIAL / ADENDA / INCREMENTO / REDUCCION / RENOVACION. */
        String tipoCambio,

        String documentoSustento,

        String observacion) {
}
