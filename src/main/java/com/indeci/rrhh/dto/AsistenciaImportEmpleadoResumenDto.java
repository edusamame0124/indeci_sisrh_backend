package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AsistenciaImportEmpleadoResumenDto {

    private Long empleadoId;
    private String dni;
    private String nombreMarcador;
    private String nombreSistema;
    private boolean empleadoEncontrado;
    private int diasLaborados;
    private int diasFalta;
    private int totalMinTardanza;
    private int minutosSalidaAnticipada;
    private int marcasIncompletas;
    private int diasObservados;
    private double remuneracionBase;
    private String baseOrigen;
    private double descuentoTardanza;
    private double descuentoFalta;
    private double descuentoTotal;
    private String estadoCabeceraPropuesto;
    private boolean conflictoExistente;
    private final List<String> advertencias = new ArrayList<>();
}
