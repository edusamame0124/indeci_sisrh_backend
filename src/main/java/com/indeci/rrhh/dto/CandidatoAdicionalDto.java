package com.indeci.rrhh.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CandidatoAdicionalDto {
    private Long empleadoId;
    private String dni;
    private String nombre;
    private String regimenLaboral;
    private LocalDate fechaIngreso;
    private String motivo; // "NUEVO_INGRESO" o "CAMBIO_ROL"
}
