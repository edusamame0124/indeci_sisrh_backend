package com.indeci.rrhh.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GoceDirectoDto {

    @NotNull(message = "El empleado es obligatorio")
    private Long empleadoId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;

    private Boolean esAdelanto;

    private String documentoSustento;

    private String motivoExcepcion;

}
