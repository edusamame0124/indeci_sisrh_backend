package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CapacitacionResponseDto {

    private Long id;

    private Long empleadoId;

    private String nombreCurso;

    private String institucion;

    private String horas;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Integer certificado;

    private Long legajoDocumentoId;
    
    public String getCertificadoTexto() {

        return certificado != null
                && certificado == 1
                ? "SI"
                : "NO";
    }
}