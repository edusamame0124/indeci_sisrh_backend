package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

/** Feriado por año (V012_18). Un día feriado no es falta. */
@Entity
@Table(name = "INDECI_FERIADO", schema = "GESTIONRRHH")
@Data
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ANIO", nullable = false)
    private Integer anio;

    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    @Column(name = "NOMBRE", nullable = false, length = 120)
    private String nombre;

    @Column(name = "TIPO", nullable = false, length = 20)
    private String tipo;

    @Column(name = "ES_IRRENUNCIABLE", nullable = false)
    private Integer esIrrenunciable;

    @Column(name = "BASE_LEGAL", length = 200)
    private String baseLegal;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;
}
