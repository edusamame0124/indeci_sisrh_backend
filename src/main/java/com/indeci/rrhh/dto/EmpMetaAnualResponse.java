package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Response de la asignación anual de meta de un empleado. */
@Data
public class EmpMetaAnualResponse {

    private Long id;
    private Long empleadoId;
    private String empleadoNombre;
    private String empleadoDni;
    private Integer anioFiscal;
    private Long metaPptoCatId;
    private String metaCodigo;
    private String centroCosto;
    private String categoriaPresupuestal;
    private String producto;
    private String actividad;
    private String finalidad;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFin;
    private String estado;
    private String origen;
    private Long loteId;
    private Integer bloqueadoPorPlanilla;
    private String observacion;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String modificadoPor;
    private LocalDateTime modificadoEn;
    private String motivoAnulacion;
}
