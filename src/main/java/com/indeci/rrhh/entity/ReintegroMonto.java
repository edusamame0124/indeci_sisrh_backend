package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_REINTEGRO_MONTO", schema = "GESTIONRRHH")
@Data
public class ReintegroMonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "PERIODO_DESTINO", nullable = false, length = 7)
    private String periodoDestino;

    // Origen OPCIONAL (F2/BLOQUE 2): en devengados judiciales / reposición la
    // trazabilidad la da el N° de resolución (sustento), no un movimiento origen.
    @Column(name = "PERIODO_ORIGEN", length = 7)
    private String periodoOrigen;

    @Column(name = "MOVIMIENTO_ORIGEN_ID")
    private Long movimientoOrigenId;

    @Column(name = "CONCEPTO_ORIGEN_CODIGO", length = 20)
    private String conceptoOrigenCodigo;

    @Column(name = "MONTO", nullable = false, precision = 19, scale = 4)
    private BigDecimal monto;

    @Column(name = "MOTIVO", nullable = false, length = 500)
    private String motivo;

    @Column(name = "SUSTENTO", nullable = false, length = 500)
    private String sustento;

    @Column(name = "ESTADO_PAGO", nullable = false, length = 50)
    private String estadoPago;

    @Column(name = "CREADO_POR", nullable = false, length = 60)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (creadoPor == null) {
            creadoPor = "SYSTEM";
        }
        if (estadoPago == null) {
            estadoPago = "PENDIENTE";
        }
    }
}
