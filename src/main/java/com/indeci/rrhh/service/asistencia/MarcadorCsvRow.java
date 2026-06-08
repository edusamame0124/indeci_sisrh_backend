package com.indeci.rrhh.service.asistencia;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class MarcadorCsvRow {

    private int numeroFila;
    private String lineaOriginal;
    private String diaSemana;
    private LocalDate fecha;
    private String dni;
    private String nombre;
    private String horaEntradaEsperada;
    private String marca1;
    private String marca2;
    private String empresa;
    private String grupo;
    private String horasTrabajadas;
    private String horasExtra25;
    private String horasExtra35;
    private String horasExtra100;
    private String horasExtraTotal;
    private String tardanza;
    private String salidaAnticipada;
    private String observacion;
    private String estadoFila = "VALIDA";
    private final List<String> errores = new ArrayList<>();
    private final List<String> advertencias = new ArrayList<>();
    private Long empleadoId;
    private String nombreSistema;
}
