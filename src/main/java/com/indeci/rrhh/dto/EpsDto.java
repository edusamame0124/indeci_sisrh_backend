package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EpsDto {
    private Long   id;
    private String codigo;
    private String nombre;
    private boolean activo;
}
