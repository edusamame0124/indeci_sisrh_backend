package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_SOLICITUD_RRHH_HIST",
        schema = "GESTIONRRHH")
@Data
public class SolicitudRrhhHist {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SOLICITUD_ID")
    private Long solicitudId;

    @Column(name = "ESTADO_ORIGEN_ID")
    private Long estadoOrigenId;

    @Column(name = "ESTADO_DESTINO_ID")
    private Long estadoDestinoId;

    @Column(name = "ACCION")
    private String accion;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "USUARIO")
    private String usuario;

    @Column(name = "FECHA")
    private LocalDateTime fecha;
}