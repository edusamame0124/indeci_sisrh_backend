package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_LEGAJO_DOCUMENTO",
        schema = "GESTIONRRHH")
@Data
public class LegajoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "CATEGORIA_ID")
    private Long categoriaId;

    @Column(name = "SUBCATEGORIA_ID")
    private Long subcategoriaId;

    @Column(name = "NOMBRE_DOCUMENTO")
    private String nombreDocumento;

    @Column(name = "NOMBRE_ARCHIVO")
    private String nombreArchivo;

    @Column(name = "RUTA_ARCHIVO")
    private String rutaArchivo;

    @Column(name = "EXTENSION")
    private String extension;

    @Column(name = "PESO_ARCHIVO")
    private Long pesoArchivo;

    @Column(name = "FECHA_DOCUMENTO")
    private LocalDate fechaDocumento;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "ORIGEN")
    private String origen;

    @Column(name = "REFERENCIA_ID")
    private Long referenciaId;

    @Column(name = "VERSION_DOC")
    private Integer versionDoc;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY")
    private String createdBy;
}