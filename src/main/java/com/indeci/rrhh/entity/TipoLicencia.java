package com.indeci.rrhh.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_TIPO_LICENCIA",
        schema = "GESTIONRRHH")
@Data
public class TipoLicencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CODIGO")
    private String codigo;

    /** SPEC_VACACIONES F9.1 — 1=licencia sin goce de haber (afecta récord y días laborados). */
    @Column(name = "ES_SIN_GOCE")
    private Integer esSinGoce;

    /** 1=requiere Resolución Directoral de sustento (auditoría Contraloría/SERVIR). */
    @Column(name = "REQUIERE_RESOLUCION")
    private Integer requiereResolucion;

    /** Código oficial Tabla 21 PLAME (SUNAT); las sin goce se agrupan en '05'. */
    @Column(name = "COD_PLAME_SUNAT")
    private String codPlameSunat;
}