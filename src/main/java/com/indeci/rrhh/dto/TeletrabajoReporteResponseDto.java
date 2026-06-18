package com.indeci.rrhh.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class TeletrabajoReporteResponseDto {

    private Long id;

    private Long empleadoId;

    private Integer mes;

    private Integer anio;

    private Long modalidadId;

    private String modalidad;

    private LocalDate fechaReporte;

    private String estado;

    private List<TeletrabajoReporteDetResponseDto>
            detalles;
}