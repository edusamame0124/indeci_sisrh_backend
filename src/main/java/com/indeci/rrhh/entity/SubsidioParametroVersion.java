package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_PARAMETRO_VERSION")
@Data
public class SubsidioParametroVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PARAMETRO_ID", nullable = false)
    private Long parametroId;

    @Column(name = "VALOR_NUMERICO", precision = 18, scale = 6)
    private BigDecimal valorNumerico;

    @Column(name = "VALOR_TEXTO", length = 500)
    private String valorTexto;

    @Column(name = "FECHA_VIG_INI", nullable = false)
    private LocalDate fechaVigIni;

    @Column(name = "FECHA_VIG_FIN")
    private LocalDate fechaVigFin;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "FUENTE_NORMATIVA", length = 200)
    private String fuenteNormativa;

    @Column(name = "MOTIVO_CAMBIO", length = 500)
    private String motivoCambio;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;

    @Column(name = "APROBADO_BY", length = 60)
    private String aprobadoBy;

    @Column(name = "APROBADO_AT")
    private LocalDateTime aprobadoAt;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
