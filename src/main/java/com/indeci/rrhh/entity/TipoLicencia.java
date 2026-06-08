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
}