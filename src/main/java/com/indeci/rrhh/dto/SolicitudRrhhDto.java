package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SolicitudRrhhDto {

	//private Long empleadoId;

	private Long tipoSolicitudId;

	private LocalDate fechaInicio;

	private LocalDate fechaFin;

	private String motivo;

	private String observacion;

	private String horaInicio;

	private String horaFin;
	private String lugarComision;
	
	
	//VALORES REQUERIDOS PARA LACTANCIA
	private LocalDate fechaNacimientoHijo; //si

	private LocalDate fechaFinPostnatal; //ya no por que se calcula con la fecha de nacimiento

	private Integer minutosIngreso;

	private Integer minutosSalida;
	
	private Long tipoDescansoMedicoId;

	private String nombreMedico;

	private String numeroColegiatura;
	
	private Long tipoLicenciaId;
	
	private String documento1;

	private String documento2;

	private Integer totalFolios;
	
	private Long tipoVacacionId;
	
	private List<SolicitudVacacionDetDto> detallesVacacion;
	
	private List<SolicitudCompensacionDetDto>
    detallesCompensacion;
	
	private Double cantidadHoras;
	//*****************************************
	private List<DocumentoAdjuntoDto>
    documentosAdjuntos;
	
	
}