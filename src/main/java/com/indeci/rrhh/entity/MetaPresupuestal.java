package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Spec 012 / C1 · P-05 — Certificación presupuestal por meta y período.
 *
 * <p>Una fila por (PERIODO_ID, META). {@code montoCertificado} es el techo que
 * transcribe Tesorería de la certificación presupuestal física; el monto
 * comprometido NO se almacena: se deriva sumando los netos de la planilla.
 */
@Entity
@Table(name = "INDECI_META_PRESUPUESTAL", schema = "GESTIONRRHH")
@Data
public class MetaPresupuestal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERIODO_ID")
    private Long periodoId;

    @Column(name = "META")
    private String meta;

    @Column(name = "CENTRO_COSTO")
    private String centroCosto;

    @Column(name = "FUENTE_FINANC")
    private String fuenteFinanc;

    /** Techo de la certificación presupuestal física (Ley 28411 / LEY-05). */
    @Column(name = "MONTO_CERTIFICADO")
    private Double montoCertificado;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
