package com.indeci.rrhh.dto;

import lombok.Data;
import java.util.List;

@Data
public class GenerarAdicionalRequestDto {
    private List<Long> empleadosIds;
}
