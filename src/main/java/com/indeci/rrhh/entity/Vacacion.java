package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "INDECI_VACACIONES",
		schema = "GESTIONRRHH")
public class Vacacion {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;
	
	@Column(name = "PERIODO_DESDE",nullable = false)
	private LocalDate periodoDesde;
	
	@Column(name="PERIODO_HASTA", nullable = false)
	private LocalDate periodoHasta;
	
	@Column(name="PERIODO", nullable = false)
	private String periodo;
	
	@Column(name = "OBSERVACION")
	private String observacion;
	
	@Column(name="ACTIVO", nullable = false)
	private Integer activo;
	
	@Column(name="CREATED_AT",insertable = false, updatable = false)
	private LocalDateTime createdAt;
	
	@Column(name = "EMPLEADO_ID")
	private Long empleadoId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
	        name = "EMPLEADO_ID",
	        insertable = false,
	        updatable = false)
	private Empleado empleado;

	@Column(name = "ANIO_PERIODO")
	private Integer anioPeriodo;

	@Column(name = "TIPO_GOCE")
	private String tipoGoce;

	@Column(name = "DIAS")
	private Integer dias;

	@Column(name = "ESTADO")
	private String estado;

	@Column(name = "ES_ADELANTO")
	private Integer esAdelanto;

	@Column(name = "DOCUMENTO_SUSTENTO")
	private String documentoSustento;

	@Column(name = "MOTIVO_EXCEPCION")
	private String motivoExcepcion;

	@Column(name = "FECHA_REINCORPORACION")
	private LocalDate fechaReincorporacion;

	@Column(name = "ORIGEN")
	private String origen;

	@Column(name = "SOLICITUD_RRHH_ID")
	private Long solicitudRrhhId;
	
	
	
}
