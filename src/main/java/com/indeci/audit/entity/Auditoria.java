package com.indeci.audit.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUDITORIA", schema = "GESTIONRRHH")
@Data
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String usuario;
    private String accion;
    private String metodo;

    private String ip;
    private String userAgent;

    private LocalDateTime fecha;

    private String detalle;
    private String estado;
}