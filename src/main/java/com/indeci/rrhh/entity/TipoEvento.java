package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F2.1 — Catálogo de tipos de evento del período (maternidad, enfermedad,
 * licencias, lactancia, paternidad, cese, etc.).
 *
 * <p>Los flags S/N controlan el comportamiento del motor de planilla y el
 * SubsidioCalculadorService:</p>
 *
 * <ul>
 *   <li>{@code afectaDiasLaborados} — el evento resta días del default
 *       (motor PASO 3 / {@code calcularDiasLaborados}).</li>
 *   <li>{@code afectaBaseAfp} / {@code afectaBaseEssalud} — el evento mantiene
 *       (S) o anula (N) la base imponible.</li>
 *   <li>{@code generaSubsidio} — dispara {@code SubsidioCalculadorService}
 *       (F2.4): cálculo de subsidio EsSalud + diferencia INDECI.</li>
 *   <li>{@code requiereAdjunto} — el service exige sustento documental
 *       vinculado a {@code INDECI_LEGAJO_DOCUMENTO} (F2.6).</li>
 *   <li>{@code permiteSolape} — permite coexistir con otro evento del mismo
 *       empleado en fechas que se traslapan (ej. lactancia parcial sobre
 *       maternidad).</li>
 * </ul>
 *
 * <p>Schema NO hardcodeado (ver [[claude-md-aspiracional]]).</p>
 */
@Entity
@Table(name = "INDECI_TIPO_EVENTO")
@Data
public class TipoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO", nullable = false, length = 30)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "AFECTA_DIAS_LABORADOS", nullable = false, length = 1)
    private String afectaDiasLaborados;

    @Column(name = "AFECTA_BASE_AFP", nullable = false, length = 1)
    private String afectaBaseAfp;

    @Column(name = "AFECTA_BASE_ESSALUD", nullable = false, length = 1)
    private String afectaBaseEssalud;

    @Column(name = "GENERA_SUBSIDIO", nullable = false, length = 1)
    private String generaSubsidio;

    @Column(name = "REQUIERE_ADJUNTO", nullable = false, length = 1)
    private String requiereAdjunto;

    @Column(name = "PERMITE_SOLAPE", nullable = false, length = 1)
    private String permiteSolape;

    /** Mapeo opcional a Anexo 2 SUNAT para PDT 601 (B3 / F4). */
    @Column(name = "CODIGO_PLAME_SUNAT", length = 6)
    private String codigoPlameSunat;

    @Column(name = "ORDEN_VISUAL", nullable = false)
    private Integer ordenVisual;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
