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
@Table(name = "INDECI_SUBSIDIO_CITT")
@Data
public class SubsidioCitt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CASO_ID", nullable = false)
    private Long casoId;

    @Column(name = "TRAMO_ID")
    private Long tramoId;

    @Column(name = "NRO_CITT", nullable = false, length = 30)
    private String nroCitt;

    @Column(name = "FECHA_EMISION")
    private LocalDate fechaEmision;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "ESTADO", nullable = false, length = 30)
    private String estado;

    @Column(name = "TIPO_DOCUMENTO", length = 30)
    private String tipoDocumento;

    @Column(name = "HASH_DOCUMENTO", length = 64)
    private String hashDocumento;

    @Column(name = "LEGAJO_DOC_ID")
    private Long legajoDocId;

    @Column(name = "ACCESO_RESTRINGIDO", nullable = false, length = 1)
    private String accesoRestringido;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;
}
