package com.indeci.rrhh.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_SOLICITUD_VACACION_DET",
        schema = "GESTIONRRHH")
@Data
public class SolicitudVacacionDet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "TIPO")
    private String tipo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "TOTAL_DIAS")
    private Double totalDias;

    /**
     * Días CALENDARIO reales del bloque (Art. 34), recalculados server-side en
     * {@code SolicitudRrhhService#guardarDetalleVacacion}. Para Fraccionamiento (FRACC_*),
     * {@code totalDias} mide días HÁBILES (Art. 35.b/c) — este campo es el que efectivamente
     * descuenta {@code VacacionSaldo.diasGozados}, porque el fin de semana atrapado dentro de
     * un fraccionamiento igual consume saldo anual aunque no sea día hábil.
     */
    @Column(name = "DIAS_CALENDARIO")
    private Double diasCalendario;

    /**
     * Hub Vacacional — FK referencial a {@code Vacacion.id}. Se llena SOLO en detalles
     * "_ACTUAL" (REPROG_ACTUAL/FRACC_ACTUAL) elegidos del dropdown de periodos programados
     * (Poka-Yoke: reemplaza fechas tipeadas por selección). Al aprobar, el motor marca ese
     * registro origen como {@code ESTADO=SUSTITUIDO}.
     */
    @Column(name = "VACACION_ORIGEN_ID")
    private Long vacacionOrigenId;

    @Column(name = "ACTIVO")
    private Integer activo;
}