package com.indeci.rrhh.dto.ir4ta;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de solo lectura del control anual de suspensión IR4ta de un trabajador
 * (Wireframe B — pestaña "Control anual"). Todos los montos son calculados; el
 * acumulado y el tope NO son editables a mano desde la ficha.
 */
@Data
public class Ir4taControlAnualDto {

    private Long empleadoId;
    private Integer anioFiscal;

    /** ¿Aplica el control? (false si no hay tope configurado o no hay constancia). */
    private boolean aplicaControl;

    // Constancia
    private boolean existeConstanciaVigente;
    private String nroConstancia;
    private String estadoConstancia;   // VIGENTE | VENCIDA | INACTIVA | FUTURA | -

    // Tope
    private String tipoTope;           // GENERAL_CAS | DIRECTOR_SIMILAR (flag manual)
    private BigDecimal topeAnual;

    // Acumulado / consumo
    private BigDecimal acumuladoIndeci;
    private BigDecimal saldoDisponible;
    private BigDecimal pctConsumido;
    private String ultimoPeriodoCalc;

    // Estado de control
    private String estadoControl;
    private String periodoExceso;
    private LocalDateTime fechaDeteccionExceso;

    // Reinicio (si fue confirmado por RR.HH.)
    private String periodoReinicio;
    private String sustentoReinicio;
    private String confirmadoPor;
    private LocalDateTime confirmadoEn;
}
