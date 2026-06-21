package com.indeci.rrhh.dto.subsidio;

import java.util.List;

public record SubsidioCasoPageDto(
        List<SubsidioCasoResponseDto> content,
        long totalElements,
        int totalPages,
        int page,
        int size) {}
