package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F2.5 — Response de eventos del período. Denormaliza datos del catálogo
 * {@link com.indeci.rrhh.entity.TipoEvento} (código, nombre, flag genera
 * subsidio) para que la UI no tenga que hacer un GET aparte.
 */
@Data
public class EventoPeriodoResponseDto {

    private Long id;
    private Long empleadoId;
    /** Denormalizado para bandeja operativa (listado global). */
    private String empleadoNombre;
    private String empleadoDni;

    private Long tipoEventoId;
    private String tipoEventoCodigo;
    private String tipoEventoNombre;
    /** S/N — si true, F2.4 SubsidioCalculadorService puede invocarse. */
    private String generaSubsidio;
    private String requiereAdjunto;

    private String periodo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasAfectos;

    private Long sustentoLegajoDocId;
    private String observacion;

    private String estado;
    private LocalDateTime createdAt;
    private String createdBy;

    private Integer duracionLegal;
    private String motivoExtension;
    private LocalDate fechaProbableParto;
    private String difierePrenatalPostnatal;
    private String tipoDocumento;
    private String nroCitt;
    private LocalDate fechaEmisionDoc;
    private List<EventoDistribucionMesDto> distribucionMensual;
}
