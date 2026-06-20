package com.indeci.rrhh.dto;

import lombok.Data;

/** Body del endpoint POST /equivalencias/detectar-auto */
@Data
public class DeteccionEquivRequestDto {
    private Integer anioOrigen;
    private Integer anioDestino;
}
