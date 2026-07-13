package com.indeci.rrhh.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * F9.3 Acumulación de vacaciones (D.S. 013-2019-PCM) — registro de AUDITORÍA, append-only.
 * Documenta que RR.HH. evaluó la acumulación de períodos sin gozar de un empleado y su
 * decisión, con sustento. NO modifica saldos: el sistema nunca fuerza pérdida ni goce
 * automático de vacaciones acumuladas.
 */
@Entity
@Table(name = "INDECI_VACACION_ACUM_DECISION", schema = "GESTIONRRHH")
@Data
public class VacacionAcumulacionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    /** Cuántos períodos sin gozar tenía el empleado AL MOMENTO de la decisión (snapshot). */
    @Column(name = "PERIODOS_PENDIENTES_AL_MOMENTO")
    private Integer periodosPendientesAlMomento;

    @Column(name = "MOTIVO_DECISION")
    private String motivoDecision;

    @Column(name = "DOCUMENTO_SUSTENTO")
    private String documentoSustento;

    @Column(name = "USUARIO_REGISTRO")
    private String usuarioRegistro;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
