package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alias de identidad del marcador (V012_19): asocia el nombre del reporte COEN
 * (que no trae DNI) con un empleado del sistema. Se llena individualmente desde
 * la pantalla de mapeo (SPEC D1) y luego auto-empareja.
 */
@Entity
@Table(name = "INDECI_EMPLEADO_MARCADOR_ALIAS", schema = "GESTIONRRHH")
@Data
public class EmpleadoMarcadorAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** Nombre del marcador normalizado (sin tildes, mayúsculas, espacios simples). */
    @Column(name = "NOMBRE_MARCADOR_NORM", nullable = false, length = 150)
    private String nombreMarcadorNorm;

    @Column(name = "NOMBRE_MARCADOR_ORIGINAL", length = 150)
    private String nombreMarcadorOriginal;

    @Column(name = "ORIGEN", nullable = false, length = 20)
    private String origen = "COEN";

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
