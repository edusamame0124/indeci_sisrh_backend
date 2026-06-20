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
    /** Hora de entrada programada (columna "Entrada" del marcador). */
    private String horaEntradaEsperada;
    /** Hora de salida programada (columna "Salida" del marcador). */
    private String salidaProgramada;
    private String marca1;
    private String marca2;
    private String marca3;
    private String marca4;
    private String empresa;
    private String grupo;
    /** Refrigerio (columna "Refrig."). HH:mm. */
    private String refrigerio;
    /** Exceso de refrigerio (columna "E/Refrig."). HH:mm. */
    private String excesoRefrigerio;
    /** Tiempo de refrigerio (columna "T/Refrig"). HH:mm. */
    private String tiempoRefrigerio;
    /** Tiempo antes de salida (columna "T/As"). HH:mm. */
    private String tiempoAntesSalida;
    private String horasTrabajadas;
    private String horasExtra25;
    private String horasExtra35;
    private String horasExtra100;
    private String horasExtraTotal;
    private String tardanza;
    /** F-B — tardanza recalculada desde la jornada configurada (min); null = usar el valor del reloj. */
    private Integer tardanzaMinCalculada;
    private String salidaAnticipada;
    private String observacion;
    private String estadoFila = "VALIDA";
    private final List<String> errores = new ArrayList<>();
    private final List<String> advertencias = new ArrayList<>();
    private Long empleadoId;
    private String nombreSistema;
}
