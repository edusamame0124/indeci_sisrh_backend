package com.indeci.rrhh.entity;

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
@Table(name = "INDECI_SUBSIDIO_CASO")
@Data
public class SubsidioCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "CODIGO_CASO", nullable = false, length = 30)
    private String codigoCaso;

    @Column(name = "TIPO_CASO", nullable = false, length = 20)
    private String tipoCaso;

    @Column(name = "ESTADO", nullable = false, length = 30)
    private String estado;

    @Column(name = "FECHA_CONTINGENCIA")
    private LocalDate fechaContingencia;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "DIAS_CONTINGENCIA")
    private Integer diasContingencia;

    @Column(name = "VERSION_CASO", nullable = false)
    private Integer versionCaso;

    @Column(name = "REGLA_VIGENCIA_ID")
    private Long reglaVigenciaId;

    @Column(name = "MODO_CALCULO", nullable = false, length = 20)
    private String modoCalculo;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;

    @Column(name = "MODIFIED_AT")
    private LocalDateTime modifiedAt;

    @Column(name = "MODIFIED_BY", length = 60)
    private String modifiedBy;
}
