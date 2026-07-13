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
    
    @Column(name = "MOSTRAR_VACACION")
    private Integer mostrarVacacion;
    
    @Column(name = "MOSTRAR_COMPENSACION")
    private Integer mostrarCompensacion;

    /**
     * 1 = una papeleta APROBADA de este tipo que cubre la fecha justifica el dia
     * (no descuenta) al cargar asistencia; 0 = no justifica (permiso sin goce).
     * Parametrizable en BD (V012_36) — RR. HH. clasifica con goce / sin goce.
     */
    @Column(name = "JUSTIFICA_ASISTENCIA")
    private Integer justificaAsistencia;

}