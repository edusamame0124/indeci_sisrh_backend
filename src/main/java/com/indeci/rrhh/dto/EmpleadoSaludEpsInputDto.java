package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpleadoSaludEpsInputDto {

    @NotBlank
    private String    tipoCobertura;   // ESSALUD | ESSALUD_EPS

    private Long      epsId;           // obligatorio si ESSALUD_EPS

    @NotNull
    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private String    documentoSustento;

    private String    observacion;
}
