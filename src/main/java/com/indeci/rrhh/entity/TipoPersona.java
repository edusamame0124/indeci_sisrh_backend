package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_TIPO_PERSONA", schema = "GESTIONRRHH")
@Data
public class TipoPersona {

    @Id
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;
}