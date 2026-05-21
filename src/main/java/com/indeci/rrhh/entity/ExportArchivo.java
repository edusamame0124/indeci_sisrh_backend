package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * B3 / M09 / V010_29 — Log de un archivo PLAME/MCPP generado.
 *
 * <p>Guarda la metadata del archivo (hash, líneas, totales, ticket MCPP).
 * La traza quién/cuándo la lleva AuditoriaAspect en TBL_LOG_AUDITORIA.
 */
@Entity
@Table(name = "INDECI_EXPORT_ARCHIVO", schema = "GESTIONRRHH")
@Data
public class ExportArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Período "YYYY-MM". */
    @Column(name = "PERIODO", nullable = false)
    private String periodo;

    /** PLAME_REM | PLAME_JOR | PLAME_SNL | MCPP_01 | MCPP_03 | MCPP_12. */
    @Column(name = "TIPO_ARCHIVO", nullable = false)
    private String tipoArchivo;

    @Column(name = "NOMBRE_ARCHIVO", nullable = false)
    private String nombreArchivo;

    /** SHA-256 del contenido (integridad / no-repudio). */
    @Column(name = "HASH_SHA256", nullable = false)
    private String hashSha256;

    @Column(name = "NRO_LINEAS", nullable = false)
    private Integer nroLineas;

    @Column(name = "TOTAL_INGRESOS")
    private BigDecimal totalIngresos;

    @Column(name = "TOTAL_DESCUENTOS")
    private BigDecimal totalDescuentos;

    @Column(name = "GENERADO_POR")
    private Long generadoPor;

    @Column(name = "FECHA_GENERADO", nullable = false)
    private LocalDateTime fechaGenerado;

    /** Ticket MCPP de Tesorería post-pago (M14). NULL hasta confirmar. */
    @Column(name = "NRO_TICKET_MCPP")
    private String nroTicketMcpp;
}
