package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_TIPO_DOCUMENTO", schema = "GESTIONRRHH")
@Data
public class TipoDocumento {

    @Id
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;
}