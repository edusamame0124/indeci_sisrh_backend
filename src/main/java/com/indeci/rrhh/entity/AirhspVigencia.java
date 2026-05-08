package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "INDECI_AIRHSP_VIGENCIA", schema = "GESTIONRRHH")
@Data
public class AirhspVigencia {

    @Id
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;
}