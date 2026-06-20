package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_REGLA_CONCEPTO")
@Data
public class SubsidioReglaConcepto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REGLA_VIGENCIA_ID", nullable = false)
    private Long reglaVigenciaId;

    @Column(name = "TIPO_SUBSIDIO", nullable = false, length = 20)
    private String tipoSubsidio;

    @Column(name = "CONCEPTO_PLANILLA_ID", nullable = false)
    private Long conceptoPlanillaId;

    @Column(name = "CODIGO_PLAME", length = 10)
    private String codigoPlame;

    @Column(name = "TIPO_IMPUTACION", nullable = false, length = 30)
    private String tipoImputacion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
