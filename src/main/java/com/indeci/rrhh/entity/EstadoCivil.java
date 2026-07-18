package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_ESTADO_CIVIL", schema = "GESTIONRRHH")
@Data
public class EstadoCivil {

    // La columna INDECI_ESTADO_CIVIL.ID se autogenera en BD (V012_37 insertó los
    // canónicos sin ID y funcionó). Sin esta estrategia, un alta por JPA falla con
    // "Identifier must be manually assigned".
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;
}