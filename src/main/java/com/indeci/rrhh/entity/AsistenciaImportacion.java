package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_ASISTENCIA_IMPORTACION", schema = "GESTIONRRHH")
@Data
public class AsistenciaImportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERIODO", nullable = false)
    private String periodo;

    @Column(name = "NOMBRE_ARCHIVO", nullable = false)
    private String nombreArchivo;

    @Column(name = "HASH_SHA256", nullable = false)
    private String hashSha256;

    @Column(name = "RUTA_ARCHIVO")
    private String rutaArchivo;

    @Column(name = "ENCODING")
    private String encoding;

    @Column(name = "USUARIO", nullable = false)
    private String usuario;

    @Column(name = "FECHA_IMPORTACION")
    private LocalDateTime fechaImportacion;

    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "ESTRATEGIA_CONFLICTO")
    private String estrategiaConflicto;

    @Column(name = "FILAS_TOTAL", nullable = false)
    private Integer filasTotal = 0;

    @Column(name = "FILAS_VALIDAS", nullable = false)
    private Integer filasValidas = 0;

    @Column(name = "FILAS_ERROR", nullable = false)
    private Integer filasError = 0;

    @Column(name = "FILAS_OBSERVADAS", nullable = false)
    private Integer filasObservadas = 0;

    @Column(name = "EMPLEADOS_PROCESADOS", nullable = false)
    private Integer empleadosProcesados = 0;

    @Column(name = "RESULTADO_JSON")
    private String resultadoJson;
}
