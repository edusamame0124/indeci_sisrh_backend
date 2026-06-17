package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MedidaDisciplinariaDto {

    private Long empleadoId;

    private String tipoMedida;

    private String descripcion;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Long legajoDocumentoId;
}