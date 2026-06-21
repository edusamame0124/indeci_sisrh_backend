package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Día calendario de la asistencia de un período (M04 / SPEC §12.2 PANTALLA-02).
 * TIPO_DIA: LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO
 *           | FERIADO | OBSERVADO.
 */
@Entity
@Table(name = "INDECI_ASISTENCIA_DETALLE", schema = "GESTIONRRHH")
@Data
public class AsistenciaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CABECERA_ID")
    private Long cabeceraId;

    @Column(name = "DIA")
    private LocalDate dia;

    @Column(name = "TIPO_DIA")
    private String tipoDia;

    @Column(name = "MINUTOS_TARDANZA")
    private Integer minutosTardanza;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "MARCA_ENTRADA", length = 16)
    private String marcaEntrada;

    @Column(name = "MARCA_SALIDA", length = 16)
    private String marcaSalida;

    @Column(name = "MARCA3", length = 16)
    private String marca3;

    @Column(name = "MARCA4", length = 16)
    private String marca4;

    @Column(name = "HORA_ENTRADA_ESPERADA", length = 16)
    private String horaEntradaEsperada;

    @Column(name = "MINUTOS_SALIDA_ANTICIPADA")
    private Integer minutosSalidaAnticipada;

    @Column(name = "HORAS_TRABAJADAS_MIN")
    private Integer horasTrabajadasMin;

    @Column(name = "HORAS_EXTRA_25_MIN")
    private Integer horasExtra25Min;

    @Column(name = "HORAS_EXTRA_35_MIN")
    private Integer horasExtra35Min;

    @Column(name = "HORAS_EXTRA_100_MIN")
    private Integer horasExtra100Min;

    @Column(name = "HORAS_EXTRA_TOTAL_MIN")
    private Integer horasExtraTotalMin;

    @Column(name = "DIA_SEMANA", length = 8)
    private String diaSemana;

    @Column(name = "ORIGEN", length = 32)
    private String origen;

    /** Decisión RR. HH. sobre la papeleta del día: 1=autorizada (Presente), 0=no autorizada (Observado descontable), null=sin decisión. */
    @Column(name = "PAPELETA_AUTORIZADA")
    private Integer papeletaAutorizada;

    @Column(name = "PAPELETA_MOTIVO_RECHAZO", length = 500)
    private String papeletaMotivoRechazo;

    @Column(name = "PAPELETA_DECISION_USUARIO", length = 100)
    private String papeletaDecisionUsuario;

    @Column(name = "PAPELETA_DECISION_FECHA")
    private LocalDateTime papeletaDecisionFecha;
}
