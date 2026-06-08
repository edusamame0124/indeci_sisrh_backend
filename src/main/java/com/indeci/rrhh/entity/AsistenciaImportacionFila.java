package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_ASISTENCIA_IMPORTACION_FILA", schema = "GESTIONRRHH")
@Data
public class AsistenciaImportacionFila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "IMPORTACION_ID", nullable = false)
    private Long importacionId;

    @Column(name = "NUMERO_FILA", nullable = false)
    private Integer numeroFila;

    @Column(name = "LINEA_ORIGINAL", length = 4000)
    private String lineaOriginal;

    @Column(name = "DNI", length = 8)
    private String dni;

    @Column(name = "FECHA")
    private LocalDate fecha;

    @Column(name = "MARCA1", length = 16)
    private String marca1;

    @Column(name = "MARCA2", length = 16)
    private String marca2;

    @Column(name = "TARDANZA_RAW", length = 16)
    private String tardanzaRaw;

    @Column(name = "OBSERVACION_MARCADOR", length = 500)
    private String observacionMarcador;

    @Column(name = "ESTADO_FILA", nullable = false)
    private String estadoFila;

    @Column(name = "ERRORES_JSON", length = 4000)
    private String erroresJson;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "HASH_ARCHIVO", length = 64)
    private String hashArchivo;

    @Column(name = "USUARIO_IMPORTACION")
    private String usuarioImportacion;

    @Column(name = "FECHA_IMPORTACION")
    private LocalDateTime fechaImportacion;
}
