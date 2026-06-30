package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FASE 2 / V010_111 — Registro de ingresos y retenciones de 5ta categoría
 * obtenidos por el empleado en otras entidades o empleadores durante un año fiscal.
 *
 * <p>Necesario para el acumulado anual de RBA (Remuneración Bruta Anual)
 * en el cálculo del Impuesto a la Renta de Quinta Categoría.</p>
 */
@Entity
@Table(name = "INDECI_EMPLEADO_OTROS_INGRESOS", schema = "GESTIONRRHH")
@Data
public class EmpleadoOtrosIngresos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    @Column(name = "MONTO_INGRESOS", nullable = false, precision = 18, scale = 6)
    private BigDecimal montoIngresos;

    @Column(name = "MONTO_RETENCIONES", nullable = false, precision = 18, scale = 6)
    private BigDecimal montoRetenciones;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
