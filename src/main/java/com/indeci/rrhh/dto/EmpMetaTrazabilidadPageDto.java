package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmpMetaTrazabilidadPageDto {

    private List<EmpMetaTrazabilidadResponse> content;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
