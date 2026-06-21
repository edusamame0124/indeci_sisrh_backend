package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmpleadoSaludEpsDto {
    private Long          id;
    private Long          empleadoId;
    private String        tipoCobertura;
    private Long          epsId;
    private String        epsNombre;
    private LocalDate     fechaInicio;
    private LocalDate     fechaFin;
    private String        estado;
    private String        documentoSustento;
    private String        observacion;
    private String        motivoAnulacion;
    private String        anuladoPor;
    private LocalDateTime anuladoEn;
    private String        creadoPor;
    private LocalDateTime creadoEn;
    private String        modificadoPor;
    private LocalDateTime modificadoEn;
}
