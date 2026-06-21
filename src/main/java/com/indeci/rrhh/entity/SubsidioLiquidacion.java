package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_LIQUIDACION")
@Data
public class SubsidioLiquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TRAMO_ID", nullable = false)
    private Long tramoId;

    @Column(name = "REGLA_VIGENCIA_ID")
    private Long reglaVigenciaId;

    @Lob
    @Column(name = "PARAMETRO_VERSION_IDS")
    private String parametroVersionIds;

    @Column(name = "CONTRAPRESTACION_DIARIA", precision = 18, scale = 4)
    private BigDecimal contraprestacionDiaria;

    @Column(name = "DIAS_LABORADOS")
    private Integer diasLaborados;

    @Column(name = "CONTRAPRESTACION_EQUIVALENTE", precision = 18, scale = 2)
    private BigDecimal contraprestacionEquivalente;

    @Column(name = "SUBSIDIO_DIARIO_ESSALUD", precision = 18, scale = 4)
    private BigDecimal subsidioDiarioEssalud;

    @Column(name = "SUBSIDIO_ESTIMADO", precision = 18, scale = 2)
    private BigDecimal subsidioEstimado;

    @Column(name = "SUBSIDIO_RECONOCIDO", precision = 18, scale = 2)
    private BigDecimal subsidioReconocido;

    @Column(name = "DIFERENCIAL_INDECI", precision = 18, scale = 2)
    private BigDecimal diferencialIndeci;

    @Column(name = "CONCILIACION_TOTAL", precision = 18, scale = 2)
    private BigDecimal conciliacionTotal;

    @Column(name = "VERSION_LIQ", nullable = false)
    private Integer versionLiq;

    @Column(name = "ES_VIGENTE", nullable = false, length = 1)
    private String esVigente;

    @Column(name = "ESTADO", nullable = false, length = 30)
    private String estado;

    @Column(name = "FORMULA_APLICADA", length = 100)
    private String formulaAplicada;

    @Lob
    @Column(name = "SNAPSHOT_JSON")
    private String snapshotJson;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;
}
