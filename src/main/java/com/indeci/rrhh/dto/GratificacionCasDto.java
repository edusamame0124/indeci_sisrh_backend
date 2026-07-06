package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * Track B F4 — Resultado de la gratificación CAS a grabar en la planilla regular.
 *
 * @param codigoBeneficio regla aplicada (GRATIFICACION_FIESTAS_PATRIAS_CAS / _NAVIDAD_CAS)
 * @param codigoMef       concepto operativo a grabar (INDECI_CONCEPTO_PLANILLA.CODIGO_MEF)
 * @param monto           monto = remuneración mensual × factor (100%)
 */
public record GratificacionCasDto(String codigoBeneficio, String codigoMef, BigDecimal monto) {}
