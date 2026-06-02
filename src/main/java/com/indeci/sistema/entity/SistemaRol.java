package com.indeci.sistema.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "INDECI_SISTEMA_ROL", schema = "GESTIONRRHH")
@Data
public class SistemaRol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SISTEMA_ID", nullable = false)
    private Long sistemaId;

    @Column(name = "CODIGO_ROL", nullable = false, length = 40)
    private String codigoRol;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    @Column(name = "DESCRIPCION", length = 300)
    private String descripcion;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
