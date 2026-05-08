package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_DEPENDENCIA", schema = "GESTIONRRHH")
@Data
public class Dependencia {

    @Id
    private Long id;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "SIGLA")
    private String sigla;
}