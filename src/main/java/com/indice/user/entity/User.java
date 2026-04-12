package com.indice.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "USERS", schema = "GESTIONRRHH")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USERNAME", unique = true)
    private String username;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "OTP_SECRET")
    private String otpSecret;

    @Column(name = "OTP_HABILITADO")
    private String otpHabilitado;

    @Column(name = "NEW_CLAVE")
    private String newClave;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}