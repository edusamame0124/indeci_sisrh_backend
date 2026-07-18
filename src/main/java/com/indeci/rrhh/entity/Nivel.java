package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_NIVEL", schema = "GESTIONRRHH")
@Data
public class Nivel {

    // La columna INDECI_NIVEL.ID se autogenera en BD (como el resto de catálogos
    // INDECI_*). El mapeo anterior omitía la estrategia, así que un alta por JPA
    // fallaba con "Identifier must be manually assigned" (visto en el import).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;
}