package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ConceptoPlanillaResponseDto {

    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String naturaleza;
    private Integer activo;

    // Spec 013 / C1 — campos MEF expuestos para el dropdown del modal
    // "Asignar Descuento / Ajuste Manual" (agrupación y filtro por tipo/SISPER).
    private String codigoMef;
    private String codigoSisper;
    private String tipoConcepto;
}