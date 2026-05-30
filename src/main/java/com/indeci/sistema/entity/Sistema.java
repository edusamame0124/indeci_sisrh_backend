package com.indeci.sistema.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Fase 3 SSO — Catálogo de sistemas de la entidad que comparten el Auth Service.
 * Tabla INDECI_SISTEMA (V010_34). CODIGO es la key del claim "sistemas" del JWT.
 */
@Entity
@Table(name = "INDECI_SISTEMA", schema = "GESTIONRRHH")
@Data
public class Sistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 150)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 500)
    private String descripcion;

    /** NULL para SISRH (selector vive dentro). Externos: http(s)://host:puerto */
    @Column(name = "URL_BASE", length = 255)
    private String urlBase;

    /** Nombre del ícono Material para la card del Portal Selector. */
    @Column(name = "ICONO", length = 50)
    private String icono;

    @Column(name = "ORDEN", nullable = false)
    private Integer orden;

    /** 1 = visible en el selector; 0 = oculto sin borrar la fila. */
    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
