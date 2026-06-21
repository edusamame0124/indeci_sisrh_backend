package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * V010_77 — Auditoría específica del módulo de metas presupuestales.
 * Complementa TBL_LOG_AUDITORIA (AuditoriaAspect, D.L. 1451).
 * Registra eventos de negocio con valores anterior/nuevo.
 */
@Entity
@Table(name = "INDECI_META_PPTO_AUD", schema = "GESTIONRRHH")
@Data
public class MetaPptoAud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → INDECI_EMP_META_ANUAL.ID (nullable) */
    @Column(name = "EMP_META_ID")
    private Long empMetaId;

    /** FK → INDECI_EMPLEADO.ID (nullable) */
    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "ANIO_FISCAL")
    private Integer anioFiscal;

    /**
     * CREAR_META | EDITAR_META | ANULAR_META | IMPORTAR_CATALOGO |
     * CREAR_ASIGNACION | EDITAR_ASIGNACION | COPIAR_ANIO_ANTERIOR |
     * APLICAR_EQUIVALENCIA | IMPORTAR_EXCEL | VALIDAR_ASIGNACION |
     * PUBLICAR_ASIGNACION | ANULAR_ASIGNACION | BLOQUEAR_POR_PLANILLA
     */
    @Column(name = "ACCION", nullable = false, length = 50)
    private String accion;

    @Lob
    @Column(name = "VALOR_ANTERIOR")
    private String valorAnterior;

    @Lob
    @Column(name = "VALOR_NUEVO")
    private String valorNuevo;

    @Column(name = "MOTIVO", length = 1000)
    private String motivo;

    @Column(name = "USUARIO", nullable = false, length = 100)
    private String usuario;

    @Column(name = "FECHA", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "IP_SESION", length = 100)
    private String ipSesion;
}
