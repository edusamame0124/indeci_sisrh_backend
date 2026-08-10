package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Refrigerio vigente del empleado logueado para una fecha (régimen u Horario Especial,
 * vía EmpleadoJornadaResolver). Alimenta el cálculo de horas efectivas en el frontend de
 * papeletas (permiso compensable y similares), para que el número que ve el usuario antes
 * de guardar coincida con lo que el backend va a calcular.
 */
@Data
public class MiJornadaRefrigerioDto {

    private String refrigerioInicio;

    private String refrigerioFin;
}
