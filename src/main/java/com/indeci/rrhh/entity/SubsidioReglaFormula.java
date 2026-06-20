package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_REGLA_FORMULA")
@Data
public class SubsidioReglaFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "REGLA_VIGENCIA_ID", nullable = false)
    private Long reglaVigenciaId;

    @Column(name = "CODIGO_FORMULA", nullable = false, length = 50)
    private String codigoFormula;

    @Lob
    @Column(name = "EXPRESION_JSON", nullable = false)
    private String expresionJson;

    @Column(name = "DESCRIPCION", length = 300)
    private String descripcion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
