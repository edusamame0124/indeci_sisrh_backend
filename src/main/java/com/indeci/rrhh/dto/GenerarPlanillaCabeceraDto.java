package com.indeci.rrhh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class GenerarPlanillaCabeceraDto {
    @NotBlank
    private String periodo;
    @NotNull
    private Long regimenLaboralId;
    
    private Long tipoContratoId;
    private Long condicionLaboralId;
    private Long modalidadCasId;

    @NotBlank
    private String concepto;
    @NotBlank
    private String tipoPlanilla;
    
    private String ordenBoleta; // Opcional, transitorio para el orden
    
    // Usado opcionalmente cuando tipoPlanilla = ADICIONAL
    private List<Long> empleadosIds;
    private List<String> conceptosSeleccionados;
    private String motivo;
    private String sustento;
}
