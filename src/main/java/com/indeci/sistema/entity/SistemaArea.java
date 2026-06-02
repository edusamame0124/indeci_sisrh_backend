package com.indeci.sistema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SISTEMA_AREA", schema = "GESTIONRRHH")
@Data
public class SistemaArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SISTEMA_ID", nullable = false)
    private Long sistemaId;

    @Column(name = "CODIGO_AREA", nullable = false, length = 40)
    private String codigoArea;

    @Column(name = "NOMBRE_AREA", nullable = false, length = 150)
    private String nombreArea;

    @Column(name = "SIGLA", length = 20)
    private String sigla;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
