package com.indeci.user.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "SS_USUARIO_ROL", schema = "GESTIONRRHH")
@IdClass(UsuarioRolId.class)
@Data
public class UsuarioRol {

    @Id
    @Column(name = "ID_USER")
    private Long userId;

    @Id
    @Column(name = "ID_ROL")
    private Long rolId;

    /** Sistema al que aplica el rol (SISRH, CONVOCATORIA, etc.). NOT NULL en Oracle. */
    @Column(name = "SISTEMA", nullable = false)
    private String sistema = "SISRH";
}