package com.indeci.rrhh.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Respuesta paginada para GET /api/rrhh/evento-periodo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoPeriodoPageDto {

    private List<EventoPeriodoResponseDto> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
