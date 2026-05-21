package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * B3 / V010_33 — Configuración institucional.
 * COD_ENTIDAD alimenta el MCPP; RUC el nombre del archivo PLAME.
 */
@Entity
@Table(name = "INDECI_ENTIDAD", schema = "GESTIONRRHH")
@Data
public class Entidad {

    @Id
    @Column(name = "COD_ENTIDAD")
    private String codEntidad;

    /** RUC para el nombre del archivo PLAME (0601+YYYY+MM+RUC). */
    @Column(name = "RUC")
    private String ruc;

    @Column(name = "RAZON_SOCIAL")
    private String razonSocial;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
