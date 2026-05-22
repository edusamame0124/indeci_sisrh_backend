package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_LEGAJO_SUBCATEGORIA",
        schema = "GESTIONRRHH")
@Data
public class LegajoSubcategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CATEGORIA_ID")
    private Long categoriaId;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "ORDEN_VISUAL")
    private Integer ordenVisual;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}