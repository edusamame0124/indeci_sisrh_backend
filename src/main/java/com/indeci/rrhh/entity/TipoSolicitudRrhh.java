package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_TIPO_SOLICITUD_RRHH",
        schema = "GESTIONRRHH"
)
@Data
public class TipoSolicitudRrhh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOMBRE")
    private String nombre;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "ACTIVO")
    private Integer activo;
    
    @Column(name = "MOSTRAR_HORAS")
    private Integer mostrarHoras;

    @Column(name = "REQUIERE_SUSTENTO")
    private Integer requiereSustento;
    
    @Column(name = "REQUIERE_LUGAR")
    private Integer requiereLugar;
    
    @Column(name = "REQUIERE_OBSERVACION")
    private Integer requiereObservacion;
    
    @Column(name = "MOSTRAR_LACTANCIA")
    private Integer mostrarLactancia;
    
    @Column(name = "MOSTRAR_DESCANSO_MEDICO")
    private Integer mostrarDescansoMedico;
    
    @Column(name = "MOSTRAR_LICENCIA")
    private Integer mostrarLicencia;
    
}