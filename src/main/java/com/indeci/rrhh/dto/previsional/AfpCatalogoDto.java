package com.indeci.rrhh.dto.previsional;

import lombok.Data;

@Data
public class AfpCatalogoDto {
    private Long    id;
    private String  codigo;
    private String  nombre;
    private boolean activo;
}
