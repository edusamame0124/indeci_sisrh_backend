package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class FamiliarDto {

    private Long empleadoId;

    private String nombreCompleto;

    private String parentesco;

    private LocalDate fechaNacimiento;

    private Long tipoDocumentoId;

    private String nroDocumento;

    private String telefono;
}