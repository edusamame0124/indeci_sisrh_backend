package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;


@Entity
@Table(name = "INDECI_EMPLEADO_PUESTO_HIST", schema = "GESTIONRRHH")
@Data
public class EmpleadoPuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "CARGO_ID")
    private Long cargoId;

    @Column(name = "NIVEL_ID")
    private Long nivelId;


    @Column(name = "OFICINA_ID")
    private Long oficinaId;

    @Column(name = "JEFE_ID")
    private Long jefeId;

    @Column(name = "ACTIVO")
    private Integer activo;
    


    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "OFICINA_ID",
            insertable = false,
            updatable = false)
    private Oficina oficina;
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "CARGO_ID",
            insertable = false,
            updatable = false)
    private Cargo cargo;
}