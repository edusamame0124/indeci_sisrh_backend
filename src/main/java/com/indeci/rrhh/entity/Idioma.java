package com.indeci.rrhh.entity;

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
        name = "INDECI_IDIOMA",
        schema = "GESTIONRRHH")
@Data
public class Idioma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "IDIOMA")
    private String idioma;

    @Column(name = "NIVEL_LECTURA")
    private String nivelLectura;

    @Column(name = "NIVEL_ESCRITURA")
    private String nivelEscritura;

    @Column(name = "NIVEL_HABLA")
    private String nivelHabla;

    @Column(name = "CERTIFICADO")
    private Integer certificado;

    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}