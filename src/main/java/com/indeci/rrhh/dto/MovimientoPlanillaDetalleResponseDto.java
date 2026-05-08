package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class MovimientoPlanillaDetalleResponseDto {

    private Long id;

    private Long conceptoPlanillaId;

    private String codigoConcepto;

    private String concepto;

    private String tipoConcepto;

    private Double monto;

    private Double cantidad;

    private String observacion;
}