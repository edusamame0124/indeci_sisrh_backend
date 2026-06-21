package com.indeci.rrhh.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Trazabilidad enriquecida de asignación anual — campos snapshot directamente de la tabla. */
@Data
@NoArgsConstructor
public class EmpMetaTrazabilidadResponse {

    private Long id;
    private String empleadoNombre;
    private String empleadoDni;
    private Integer anioFiscal;
    private String metaCodigo;
    private String centroCosto;
    private String categoriaPresupuestal;
    private String producto;
    private String actividad;
    private String finalidad;
    private String estado;
    private String origen;
    private Integer bloqueadoPorPlanilla;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String observacion;
}
