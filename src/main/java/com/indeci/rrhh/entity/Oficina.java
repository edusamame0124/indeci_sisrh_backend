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

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "SIGLA")
    private String sigla;

    @Column(name = "ACTIVO")
    private Integer activo;
    
    @Column(name = "ESTRUCTURA_ORGANICA_ID")
    private Long estructuraOrganicaId;
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ESTRUCTURA_ORGANICA_ID",
            insertable = false,
            updatable = false)
    private EstructuraOrganica estructuraOrganica;
    
    
    
}