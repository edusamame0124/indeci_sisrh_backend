package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Respuesta paginada para GET /api/rrhh/persona/page. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonaResumenPageDto {
    private List<PersonaResumenDto> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
