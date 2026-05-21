package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * B3 / M09 / V010_30 — Catálogo Tabla 21 SUNAT de tipos de suspensión/licencia.
 * PK natural = código SUNAT (01, 02, 03, …).
 */
@Entity
@Table(name = "INDECI_CAT_SUSPENSION_SUNAT", schema = "GESTIONRRHH")
@Data
public class CatSuspensionSunat {

    @Id
    @Column(name = "COD_SUSPENSION")
    private String codSuspension;

    @Column(name = "DESCRIPCION", nullable = false)
    private String descripcion;

    /** SUBSIDIADO | NO_LABORADO_NO_SUB | ESPECIAL. */
    @Column(name = "TIPO_PLAME", nullable = false)
    private String tipoPlame;

    @Column(name = "REQUIERE_CMP", nullable = false)
    private String requiereCmp;

    @Column(name = "REQUIERE_RESOLUCION", nullable = false)
    private String requiereResolucion;

    /** S/N — N no se declara en el .snl (solo .jor), ej: cód 21 Lactancia. */
    @Column(name = "VA_EN_SNL", nullable = false)
    private String vaEnSnl;

    /** Código PLAME de descuento gatillado (ej: cód 23 → 2046). NULL si no aplica. */
    @Column(name = "COD_DESCUENTO_PLAME")
    private String codDescuentoPlame;

    @Column(name = "SUSTENTO_LEGAL")
    private String sustentoLegal;
}
