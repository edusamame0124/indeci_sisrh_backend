package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SolicitudRrhhResponseDto {

    private Long id;

    private Long empleadoId;

    private String empleado;

    private Long tipoSolicitudId;

    private String tipoSolicitud;

    private Long estadoSolicitudId;

    private String estadoSolicitud;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Double cantidadDias;

    private String motivo;

    private String observacion;

    private Long aprobadoPor;

    private String aprobadoPorNombre;

    private LocalDateTime fechaAprobacion;

    private String archivoSustento;

    
    private Integer activo;
    private String horaInicio;

    private String horaFin;

    private Double cantidadHoras;
    
    private String lugarComision;
    
    private LocalDate fechaNacimientoHijo;

    private LocalDate fechaFinPostnatal;

    private Integer minutosIngreso;

    private Integer minutosSalida;
    
    private Long tipoDescansoMedicoId;

    private String tipoDescansoMedico;

    private String nombreMedico;

    private String numeroColegiatura;
    
    private Long tipoLicenciaId;

    private String tipoLicencia;
    
    private String documento1;

    private String documento2;

    private Integer totalFolios;
    
    private Long tipoVacacionId;

    private String tipoVacacion;
    
    private List<SolicitudVacacionDetDto> detallesVacacion;
    
    
}