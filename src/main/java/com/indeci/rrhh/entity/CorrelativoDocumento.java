package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * B3 / M14 / V010_32 — Correlativo de NRO_PLANILLA MCPP.
 *
 * <p>PK surrogate IDENTITY + clave natural única (entidad, año, mes, tipo).
 * El service lo bloquea con lock pesimista para emitir números secuenciales.
 */
@Entity
@Table(name = "INDECI_CORRELATIVO_DOCUMENTO", schema = "GESTIONRRHH")
@Data
public class CorrelativoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COD_ENTIDAD", nullable = false)
    private String codEntidad;

    @Column(name = "ANIO", nullable = false)
    private Integer anio;

    @Column(name = "MES", nullable = false)
    private Integer mes;

    /** MCPP | MCPP_01 | MCPP_03 | MCPP_12 | PLAME. */
    @Column(name = "TIPO_DOCUMENTO", nullable = false)
    private String tipoDocumento;

    @Column(name = "ULTIMO_NRO", nullable = false)
    private Long ultimoNro;
}
