package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/** Tramo mensual enviado/recibido en el CRUD de evento maternidad. */
@Data
public class EventoDistribucionMesDto {

    private String periodo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Integer diasSubsidio;
    private String afectaDiasLaborados;
    private String estadoTramo;
}
