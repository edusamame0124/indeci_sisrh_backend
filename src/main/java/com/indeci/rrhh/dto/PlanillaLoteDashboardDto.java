package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanillaLoteDashboardDto {
    private Long id;
    private String periodo;
    private String regimenLaboralCodigo;
    private String tipoPlanilla;
    private Integer correlativo;
    private String estado;
    private LocalDateTime creadoEn;
    private Long cantidadEmpleados;
    private Double montoTotalNeto;

    public String getDescripcionConcatenada() {
        String base = tipoPlanilla != null && tipoPlanilla.contains("ADICIONAL") 
                ? "Planilla Adicional " + (correlativo != null ? correlativo : "") 
                : "Planilla Ordinaria";
        
        return base.trim() + (regimenLaboralCodigo != null ? " - " + regimenLaboralCodigo : "");
    }
}
