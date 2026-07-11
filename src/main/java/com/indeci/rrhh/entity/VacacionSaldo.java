package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Saldo de vacaciones por empleado y año (SPEC §12.2 PANTALLA-08).
 * El saldo de días se deriva: DIAS_GANADOS − DIAS_GOZADOS.
 */
@Entity
@Table(name = "INDECI_VACACION_SALDO", schema = "GESTIONRRHH")
@Data
public class VacacionSaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "ANIO")
    private Integer anio;

    @Column(name = "DIAS_GANADOS")
    private Double diasGanados;

    @Column(name = "DIAS_GOZADOS")
    private Double diasGozados;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "ACTIVO")
    private Integer activo;

    /** SPEC_VACACIONES F8 — MIGRACION_INICIAL_2026 (línea base Excel) | MOTOR (automatizado). */
    @Column(name = "ORIGEN")
    private String origen;

    /** SPEC_VACACIONES F8 — fecha de corte del cálculo del Excel (col K). */
    @Column(name = "FECHA_CORTE")
    private java.time.LocalDate fechaCorte;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
