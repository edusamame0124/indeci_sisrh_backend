package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Día calendario de la asistencia de un período (M04 / SPEC §12.2 PANTALLA-02).
 * TIPO_DIA: LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO.
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
}
