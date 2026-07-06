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

    /** F2/F0 — clasificación laboral para el filtrado en Planilla Adicional. */
    private Long tipoContratoId;
    private Long condicionLaboralId;
    private Long modalidadCasId;
}
