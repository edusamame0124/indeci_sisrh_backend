package com.indeci.rrhh.dto.ir4ta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** DTO para anulación lógica de una vigencia IR4ta. */
@Data
public class Ir4taConfigAnularInputDto {

    @NotBlank(message = "El motivo de anulación es obligatorio.")
    @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres.")
    private String motivo;
}
