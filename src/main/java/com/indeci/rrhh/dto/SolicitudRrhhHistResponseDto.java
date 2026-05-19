package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SolicitudRrhhHistResponseDto {

    private Long id;

    private String estadoOrigen;

    private String estadoDestino;

    private String accion;

    private String observacion;

    private String usuario;

    private LocalDateTime fecha;
}