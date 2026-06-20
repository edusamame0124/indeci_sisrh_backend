package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_EPS", schema = "GESTIONRRHH")
@Data
public class Eps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "NOMBRE", nullable = false, length = 200)
    private String nombre;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;
}
