package com.indeci.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "SS_USUARIO_PERMISO_DENY", schema = "GESTIONRRHH")
@IdClass(UsuarioPermisoDenyId.class)
@Data
public class UsuarioPermisoDeny {

    @Id
    @Column(name = "ID_USER")
    private Long userId;

    @Id
    @Column(name = "ID_PERMISO")
    private Long permisoId;
}