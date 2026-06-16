package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Log inmutable de acciones sobre parámetros AFP/ONP.
 * Spec V010_70 — trazabilidad D.L. 1451.
 */
@Entity
@Table(name = "INDECI_PREVISIONAL_LOG", schema = "GESTIONRRHH")
@Data
public class PrevisionalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AFP | ONP. */
    @Column(name = "TIPO", nullable = false, length = 10)
    private String tipo;

    @Column(name = "AFP_ID")
    private Long afpId;

    @Column(name = "AFP_NOMBRE", length = 100)
    private String afpNombre;

    /** CREAR | EDITAR | CERRAR | DUPLICAR. */
    @Column(name = "ACCION", nullable = false, length = 20)
    private String accion;

    @Column(name = "DESCRIPCION", nullable = false, length = 500)
    private String descripcion;

    @Column(name = "PERIODO_AFECTADO", length = 6)
    private String periodoAfectado;

    @Column(name = "USUARIO", nullable = false, length = 100)
    private String usuario;

    @Column(name = "FECHA", nullable = false)
    private LocalDateTime fecha;
}
