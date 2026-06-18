package com.indeci.rrhh.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class TeletrabajoReporteDto {

    private Long empleadoId;

    private Integer mes;

    private Integer anio;

    private Long modalidadId;

    private LocalDate fechaReporte;

    private List<TeletrabajoReporteDetDto>
            detalles;
}