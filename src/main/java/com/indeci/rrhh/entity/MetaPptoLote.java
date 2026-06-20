package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * V010_77 — Cabecera de proceso masivo de asignación anual de metas.
 * CODIGO_LOTE generado por la aplicación (UUID).
 * Los totales se actualizan al finalizar el proceso.
 */
@Entity
@Table(name = "INDECI_META_PPTO_LOTE", schema = "GESTIONRRHH")
@Data
public class MetaPptoLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO_LOTE", nullable = false, length = 50, unique = true)
    private String codigoLote;

    @Column(name = "ANIO_ORIGEN")
    private Integer anioOrigen;

    @Column(name = "ANIO_DESTINO", nullable = false)
    private Integer anioDestino;

    /**
     * CARGA_CATALOGO | COPIA_ANIO_ANTERIOR | APLICACION_EQUIVALENCIAS |
     * IMPORTACION_EXCEL | PUBLICACION_ANUAL | REGULARIZACION
     */
    @Column(name = "TIPO_PROCESO", nullable = false, length = 40)
    private String tipoProceso;

    /** CREADO | PROCESANDO | VALIDADO | OBSERVADO | PUBLICADO | ANULADO | ERROR */
    @Column(name = "ESTADO", nullable = false, length = 30)
    private String estado = "CREADO";

    @Column(name = "TOTAL_EMPLEADOS", nullable = false)
    private Integer totalEmpleados = 0;

    @Column(name = "TOTAL_ASIGNADOS", nullable = false)
    private Integer totalAsignados = 0;

    @Column(name = "TOTAL_OBSERVADOS", nullable = false)
    private Integer totalObservados = 0;

    @Column(name = "TOTAL_ERRORES", nullable = false)
    private Integer totalErrores = 0;

    @Column(name = "TOTAL_SIN_EQUIV", nullable = false)
    private Integer totalSinEquiv = 0;

    @Column(name = "TOTAL_INACTIVOS", nullable = false)
    private Integer totalInactivos = 0;

    @Column(name = "TOTAL_DUPLICADOS", nullable = false)
    private Integer totalDuplicados = 0;

    @Column(name = "ARCHIVO_ORIGEN", length = 500)
    private String archivoOrigen;

    @Column(name = "OBSERVACION", length = 1000)
    private String observacion;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "FINALIZADO_EN")
    private LocalDateTime finalizadoEn;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;

    @Column(name = "MOTIVO_ANULACION", length = 1000)
    private String motivoAnulacion;
}
