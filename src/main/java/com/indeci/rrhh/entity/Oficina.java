package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_OFICINA",
       schema = "GESTIONRRHH")
@Data
public class Oficina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SEDE_ID")
    private Long sedeId;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "SIGLA")
    private String sigla;

    @Column(name = "ACTIVO")
    private Integer activo;
}