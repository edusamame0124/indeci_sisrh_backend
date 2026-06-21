package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/** Request para asignar o actualizar la meta anual de un empleado. */
@Data
public class EmpMetaAnualDto {

    private Long empleadoId;
    private Integer anioFiscal;
    private Long metaPptoCatId;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFin;
    private String observacion;
    /** Motivo requerido solo al anular. */
    private String motivoAnulacion;
}
