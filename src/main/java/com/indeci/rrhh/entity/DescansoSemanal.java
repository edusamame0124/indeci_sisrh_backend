package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Día de descanso semanal (V012_18). {@code diaIso} 1=Lunes..7=Domingo.
 * {@code regimenLaboralId} NULL = aplica a todos los regímenes.
 */
@Entity
@Table(name = "INDECI_DESCANSO_SEMANAL", schema = "GESTIONRRHH")
@Data
public class DescansoSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REGIMEN_LABORAL_ID")
    private Long regimenLaboralId;

    @Column(name = "DIA_ISO", nullable = false)
    private Integer diaIso;

    @Column(name = "DIA_NOMBRE", length = 10)
    private String diaNombre;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;
}
