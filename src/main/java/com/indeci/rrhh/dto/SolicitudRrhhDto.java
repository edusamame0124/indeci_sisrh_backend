package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SolicitudRrhhDto {

	private Long empleadoId;

	private Long tipoSolicitudId;

	private LocalDate fechaInicio;

	private LocalDate fechaFin;

	private String motivo;

	private String observacion;

	private String archivoSustento;

	private String horaInicio;

	private String horaFin;
}