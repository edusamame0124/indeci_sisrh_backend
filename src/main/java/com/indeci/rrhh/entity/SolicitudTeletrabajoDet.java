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
 * Actividad del día de una papeleta de Teletrabajo (Ley N° 31572 / SERVIR).
 * Tabla hija de INDECI_SOLICITUD_RRHH — una fila por actividad reportada.
 * Patrón: {@link SolicitudVacacionDet}.
 */
@Entity
@Table(
        name = "INDECI_SOLICITUD_TELETRABAJO_DET",
        schema = "GESTIONRRHH")
@Data
public class SolicitudTeletrabajoDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "NRO_ORDEN")
    private Integer nroOrden;

    @Column(name = "ACTIVIDAD")
    private String actividad;

    @Column(name = "MEDIO_VERIFICACION")
    private String medioVerificacion;

    @Column(name = "EVIDENCIA_ARCHIVO")
    private String evidenciaArchivo;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
