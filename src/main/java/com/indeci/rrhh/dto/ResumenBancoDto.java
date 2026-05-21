package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Resumen de abonos agrupado por banco (SPEC §12.2 PANTALLA-07 — Archivo Bancos).
 */
@Data
public class ResumenBancoDto {

    /** Nombre del banco (BCP | BBVA | SCOTIABANK | ...). */
    private String banco;

    /** Número de abonos del banco en el período. */
    private Integer cantidad;

    /** Suma de los netos a abonar por el banco. */
    private Double totalNeto;

    /** Detalle de los abonos del banco. */
    private List<AbonoBancoResponseDto> abonos;
}
