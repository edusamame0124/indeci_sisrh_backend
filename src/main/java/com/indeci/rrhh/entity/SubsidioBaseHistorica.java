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
@Table(name = "INDECI_SUBSIDIO_BASE_HISTORICA")
@Data
public class SubsidioBaseHistorica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CASO_ID", nullable = false)
    private Long casoId;

    @Column(name = "REGLA_VIGENCIA_ID")
    private Long reglaVigenciaId;

    @Column(name = "MESES_EVALUADOS", nullable = false)
    private Integer mesesEvaluados;

    @Column(name = "DIVISOR_PROMEDIO", nullable = false)
    private Integer divisorPromedio;

    @Column(name = "TOPE_MENSUAL", precision = 18, scale = 2)
    private BigDecimal topeMensual;

    @Column(name = "BASE_RECONOCIDA", precision = 18, scale = 2)
    private BigDecimal baseReconocida;

    @Column(name = "FUENTE", nullable = false, length = 20)
    private String fuente;

    @Column(name = "VERSION_BASE", nullable = false)
    private Integer versionBase;

    @Column(name = "ES_VIGENTE", nullable = false, length = 1)
    private String esVigente;

    @Lob
    @Column(name = "SNAPSHOT_JSON")
    private String snapshotJson;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
