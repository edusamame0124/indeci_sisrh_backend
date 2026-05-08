package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
    name = "INDECI_REGIMEN_PENSIONARIO",
    schema = "GESTIONRRHH"
)
@Data
public class RegimenPensionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "TIPO")
    private String tipo;

    @Column(name = "ACTIVO")
    private Integer activo;
}