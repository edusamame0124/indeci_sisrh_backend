package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_ESSALUD_VIGENCIA", schema = "GESTIONRRHH")
@Data
public class EssaludVigencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ANIO_VIGENCIA", nullable = false)
    private Integer anioVigencia;

    @Column(name = "VIGENCIA_INICIO", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "VIGENCIA_FIN")
    private LocalDate vigenciaFin;

    @Column(name = "UIT_VIGENTE", nullable = false, precision = 10, scale = 2)
    private BigDecimal uitVigente;

    @Column(name = "PCT_BASE_CAS", nullable = false, precision = 6, scale = 4)
    private BigDecimal pctBaseCas;

    @Column(name = "PCT_ESSALUD", nullable = false, precision = 6, scale = 4)
    private BigDecimal pctEssalud;

    @Column(name = "PCT_ESSALUD_EPS", nullable = false, precision = 6, scale = 4)
    private BigDecimal pctEssaludEps;

    @Column(name = "PCT_CREDITO_EPS", nullable = false, precision = 6, scale = 4)
    private BigDecimal pctCreditoEps;

    @Column(name = "FUENTE_OFICIAL", nullable = false, length = 500)
    private String fuenteOficial;

    @Column(name = "URL_FUENTE_OFICIAL", length = 1000)
    private String urlFuenteOficial;

    @Column(name = "FECHA_PUBLICACION")
    private LocalDate fechaPublicacion;

    @Column(name = "OBSERVACION", length = 1000)
    private String observacion;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "BLOQUEADO_POR_PLANILLA", nullable = false)
    private Integer bloqueadoPorPlanilla;

    @Column(name = "MOTIVO_ANULACION", length = 500)
    private String motivoAnulacion;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
