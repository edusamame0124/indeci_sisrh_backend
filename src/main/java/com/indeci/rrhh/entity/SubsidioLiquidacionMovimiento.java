package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SUBSIDIO_LIQUIDACION_MOVIMIENTO")
@Data
public class SubsidioLiquidacionMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LIQUIDACION_ID", nullable = false)
    private Long liquidacionId;

    @Column(name = "MOVIMIENTO_PLANILLA_ID")
    private Long movimientoPlanillaId;

    @Column(name = "MOVIMIENTO_DET_ID")
    private Long movimientoDetId;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "TIPO_IMPUTACION", nullable = false, length = 30)
    private String tipoImputacion;

    @Column(name = "MONTO", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
