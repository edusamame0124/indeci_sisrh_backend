package com.indeci.rrhh.dto.previsional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para la anulación lógica de una vigencia AFP/ONP.
 * El endpoint POST /eliminar NO borra físicamente el registro;
 * cambia ESTADO → ANULADO y registra motivo + trazabilidad.
 */
@Data
public class AnularVigenciaRequestDto {

    @NotBlank(message = "El motivo de eliminación/anulación es obligatorio.")
    @Size(min = 10, max = 1000,
          message = "El motivo debe tener entre 10 y 1000 caracteres.")
    private String motivo;
}
