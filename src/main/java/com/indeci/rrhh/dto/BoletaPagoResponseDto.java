package com.indeci.rrhh.dto;

import lombok.Data;
import java.util.List;

@Data
public class BoletaPagoResponseDto {
    // 1. Metadata Inmutable
    private String periodo;
    private String nombreCompleto;
    private String dni;
    
    // Snapshots Inmutables
    private String regimenLaboral;
    private String nivelRemunerativo;
    private String cuentaBancaria;
    private String modalidad;
    private Integer diasLaborados;

    // 2. Colecciones Dinámicas Iterables
    private List<ConceptoBoletaDto> ingresos;
    private List<ConceptoBoletaDto> descuentos;
    private List<ConceptoBoletaDto> aportes; // Essalud, etc (opcional o se suma en ingresos/descuentos)

    // 3. Totales
    private Double totalIngresos;
    private Double totalDescuentos;
    private Double netoPagar;
}
