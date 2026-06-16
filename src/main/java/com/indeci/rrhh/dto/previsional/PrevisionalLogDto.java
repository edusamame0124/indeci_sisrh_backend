package com.indeci.rrhh.dto.previsional;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrevisionalLogDto {
    private Long          id;
    private String        tipo;
    private String        afpNombre;
    private String        accion;
    private String        descripcion;
    private String        usuario;
    private LocalDateTime fecha;
    private String        periodoAfectado;
}
