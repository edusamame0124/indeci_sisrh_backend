package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_PERSONA", schema = "GESTIONRRHH")
@Data
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "NOMBRE_COMPLETO")
    private String nombreCompleto;

    @Column(name = "DNI")
    private String dni;

    @Column(name = "FECHA_NACIMIENTO")
    private java.util.Date fechaNacimiento;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "TELEFONO")
    private String telefono;

    @Column(name = "DIRECCION")
    private String direccion;

    @Column(name = "DISTRITO_ID")
    private String distritoId;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

}