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

    // 3. Totales (sección regular)
    private Double totalIngresos;
    private Double totalDescuentos;
    private Double netoPagar;

    // 4. Track B — Sección AGUINALDO consolidada (opción A). null si no hay
    //    aguinaldo del período → boleta regular idéntica a la actual (caso b).
    private AguinaldoSeccionDto aguinaldo;

    /** Neto total del documento = neto regular + neto aguinaldo (solo se muestra si hay aguinaldo). */
    private Double netoTotal;
}
