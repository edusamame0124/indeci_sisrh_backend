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
@Table(
        name = "INDECI_RECONOCIMIENTO",
        schema = "GESTIONRRHH")
@Data
public class Reconocimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "TIPO_RECONOCIMIENTO")
    private String tipoReconocimiento;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "FECHA_RECONOCIMIENTO")
    private LocalDate fechaReconocimiento;

    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}