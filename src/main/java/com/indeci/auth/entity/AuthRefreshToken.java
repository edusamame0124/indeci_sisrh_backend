package com.indeci.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "AUTH_REFRESH_TOKEN", schema = "GESTIONRRHH")
@Data
public class AuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USUARIO")
    private String usuario;

    @Column(name = "TOKEN")
    private String token;

    @Column(name = "FECHA_EXPIRACION")
    private LocalDateTime fechaExpiracion;

    @Column(name = "ACTIVO")
    private String activo;

    @Column(name = "IP")
    private String ip;

    @Column(name = "USER_AGENT")
    private String userAgent;

    @Column(name = "FECHA_CREACION")
    private LocalDateTime fechaCreacion;

    @Column(name = "FECHA_REVOCACION")
    private LocalDateTime fechaRevocacion;

    @Column(name = "MOTIVO_REVOCACION")
    private String motivoRevocacion;
}