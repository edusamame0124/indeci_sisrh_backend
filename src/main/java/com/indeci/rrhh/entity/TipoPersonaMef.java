package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_TIPO_PERSONA_MEF", schema = "GESTIONRRHH")
@Data
public class TipoPersonaMef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "FECHA_REGISTRO")
    private LocalDateTime fechaRegistro;
}
