package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class SolicitudVacacionDetDto {

    private String tipo;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Double totalDias;

    /**
     * Días CALENDARIO reales (Art. 34) — solo informativo en la respuesta (el cliente nunca lo
     * envía ni se usa para validar). Para Fraccionamiento difiere de {@code totalDias} (hábiles,
     * Art. 35.b/c); es el que efectivamente descuenta el saldo anual.
     */
    private Double diasCalendario;

    /** Hub Vacacional — id del período origen elegido del dropdown (solo en detalles "_ACTUAL"). */
    private Long vacacionOrigenId;
}