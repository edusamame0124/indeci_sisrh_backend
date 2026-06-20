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
@Table(name = "INDECI_SUBSIDIO_REGLA_VIGENCIA")
@Data
public class SubsidioReglaVigencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO", nullable = false, length = 50)
    private String codigo;

    @Column(name = "VERSION", nullable = false, length = 20)
    private String version;

    @Column(name = "FECHA_VIG_INI", nullable = false)
    private LocalDate fechaVigIni;

    @Column(name = "FECHA_VIG_FIN")
    private LocalDate fechaVigFin;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "DESCRIPCION", length = 500)
    private String descripcion;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
