package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO", schema = "GESTIONRRHH")
@Data
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERSONA_ID")
    private Long personaId;

    @Column(name = "CODIGO_INTERNO")
    private String codigoInterno;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

}