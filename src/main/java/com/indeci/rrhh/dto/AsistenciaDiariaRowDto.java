package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fila de consulta diaria de asistencia (M04 — lista por fecha/DNI).
 */
@Data
public class AsistenciaDiariaRowDto {

    private Long detalleId;
    private Long cabeceraId;
    private Long empleadoId;
    private String dni;
    private String nombreCompleto;
    private LocalDate fecha;
    private String marcaEntrada;
    private String marcaSalida;
    private String tipoDia;
    private Integer horasTrabajadasMin;
    private Integer minutosSalidaAnticipada;
    private String periodo;
    private String origen;
    private Integer minutosTardanza;
    private String observacion;
    private String marca3;
    private String marca4;
    private String horaEntradaEsperada;
    private Integer horasExtra25Min;
    private Integer horasExtra35Min;
    private Integer horasExtra100Min;
    private Integer horasExtraTotalMin;

    // ---- Permiso / Papeleta aprobada que cubre el día (INDECI_SOLICITUD_RRHH, estado 9) ----
    private boolean tienePapeletaAprobada;
    private String papeletaTipo;
    private String papeletaMotivo;
    private String papeletaHoraInicio;
    private String papeletaHoraFin;
    private Double papeletaCantidadHoras;

    /** 1 = autorizada, 0 = no autorizada, null = sin decisión. */
    private Integer papeletaAutorizada;
    private String papeletaMotivoRechazo;
    private String papeletaDecisionUsuario;
    private LocalDateTime papeletaDecisionFecha;

    // ---- Teletrabajo ----
    private boolean tieneTeletrabajo;
}
