package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** F9.3 — registro de la decisión de RR.HH. sobre la acumulación de períodos de un empleado. */
@Data
public class AcumulacionDecisionInputDto {

    @NotBlank(message = "El motivo de la decisión es obligatorio")
    private String motivoDecision;

    private String documentoSustento;
}
