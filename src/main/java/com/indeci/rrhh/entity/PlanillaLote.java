package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_PLANILLA_LOTE", schema = "GESTIONRRHH")
@Data
public class PlanillaLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERIODO", nullable = false)
    private String periodo;

    @Column(name = "REGIMEN_LABORAL_CODIGO")
    private String regimenLaboralCodigo;

    @Column(name = "TIPO_PLANILLA", nullable = false)
    private String tipoPlanilla;

    @Column(name = "CONCEPTO_PLANILLA")
    private String conceptoPlanilla;

    @Column(name = "CORRELATIVO")
    private Integer correlativo;

    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "CREADO_POR", updatable = false)
    private String creadoPor;

    @Column(name = "CREADO_EN", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MOTIVO", length = 500)
    private String motivo;

    @Column(name = "SUSTENTO", length = 500)
    private String sustento;

    /**
     * Track B F1 — Tipo de proceso derivado del {@code tipoPlanilla} legacy.
     * Método derivado (no columna): no se persiste ni rompe el esquema.
     */
    @Transient
    public TipoProceso getTipoProceso() {
        return TipoProceso.fromTipoPlanilla(this.tipoPlanilla);
    }

    @PrePersist
    protected void onCreate() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (creadoPor == null) {
            creadoPor = "SYSTEM";
        }
        if (estado == null) {
            estado = "BORRADOR";
        }
    }
}
