package com.indice.user.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "SS_PERMISO", schema = "GESTIONRRHH")
@Data
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PERMISO")
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "DESCRIPCION")
    private String descripcion;

    @Column(name = "TIPO")
    private String tipo;

    @Column(name = "RUTA")
    private String ruta;

    @Column(name = "ORDEN")
    private Integer orden;

    @Column(name = "DESPLEGABLE")
    private String desplegable;

    @Column(name = "ACTIVO")
    private String activo;

    @Column(name = "PADRE_CODIGO")
    private String padreCodigo;
}