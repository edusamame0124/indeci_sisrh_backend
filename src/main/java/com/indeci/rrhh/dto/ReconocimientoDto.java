package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class ReconocimientoDto {

    private Long empleadoId;

    private String tipoReconocimiento;

    private String descripcion;

    private LocalDate fechaReconocimiento;

    private Long legajoDocumentoId;
}