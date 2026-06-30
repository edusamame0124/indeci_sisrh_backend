package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_MODALIDAD_CAS", schema = "GESTIONRRHH")
@Data
public class ModalidadCas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "ACTIVO")
    private Integer activo;
}
