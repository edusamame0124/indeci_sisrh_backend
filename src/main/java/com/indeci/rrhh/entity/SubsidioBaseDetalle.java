package com.indeci.rrhh.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_BASE_DETALLE")
@Data
public class SubsidioBaseDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "BASE_HISTORICA_ID", nullable = false)
    private Long baseHistoricaId;

    @Column(name = "PERIODO", nullable = false, length = 6)
    private String periodo;

    @Column(name = "REMUNERACION_REAL", nullable = false, precision = 18, scale = 2)
    private BigDecimal remuneracionReal;

    @Column(name = "TOPE_APLICADO", precision = 18, scale = 2)
    private BigDecimal topeAplicado;

    @Column(name = "BASE_COMPUTABLE", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseComputable;

    @Lob
    @Column(name = "INCIDENCIAS_JSON")
    private String incidenciasJson;

    /** Etiqueta de trazabilidad del mes: NORMAL | LSGR | FALTA | REINTEGRO | PARCIAL. */
    @Column(name = "INCIDENCIA", length = 20)
    private String incidencia;

    /** 'S' si la fila fue ingresada/ajustada manualmente por RR. HH. */
    @Column(name = "ES_MANUAL", length = 1)
    private String esManual;

    @Column(name = "FUENTE_MOVIMIENTO_ID")
    private Long fuenteMovimientoId;
}
