package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuración de jornada y tolerancias por régimen laboral (M04).
 * Una fila por régimen (UK REGIMEN_LABORAL_ID).
 */
@Entity
@Table(name = "INDECI_JORNADA_REGIMEN", schema = "GESTIONRRHH")
@Data
public class JornadaRegimen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REGIMEN_LABORAL_ID", nullable = false)
    private Long regimenLaboralId;

    @Column(name = "HORA_INGRESO", length = 5)
    private String horaIngreso;

    @Column(name = "HORA_SALIDA", length = 5)
    private String horaSalida;

    @Column(name = "REFRIGERIO_INICIO", length = 5)
    private String refrigerioInicio;

    @Column(name = "REFRIGERIO_FIN", length = 5)
    private String refrigerioFin;

    @Column(name = "TOLERANCIA_INGRESO_MIN", nullable = false)
    private Integer toleranciaIngresoMin = 0;

    @Column(name = "TOLERANCIA_ALMUERZO_MIN", nullable = false)
    private Integer toleranciaAlmuerzoMin = 0;

    /** V010_95 — umbral diario: día con tardanza > este valor se descuenta completo (Descuento 1). */
    @Column(name = "UMBRAL_TARDANZA_DIARIA_MIN", nullable = false)
    private Integer umbralTardanzaDiariaMin = 10;

    /** V010_95 — tope mensual: tardanzas ≤ umbral se acumulan; el exceso se descuenta (Descuento 2). */
    @Column(name = "TOPE_TARDANZA_MENSUAL_MIN", nullable = false)
    private Integer topeTardanzaMensualMin = 60;

    @Column(name = "JORNADA_HORAS", nullable = false)
    private BigDecimal jornadaHoras = BigDecimal.valueOf(8);

    /** SPEC_VACACIONES F9.1 — días/semana del régimen para el récord vacacional (5=210 / 6=260). */
    @Column(name = "DIAS_SEMANA")
    private Integer diasSemana = 5;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
