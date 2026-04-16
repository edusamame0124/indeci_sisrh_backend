package com.indeci.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "SS_ROL_PERMISO", schema = "GESTIONRRHH")
@IdClass(RolPermisoId.class)
@Data
public class RolPermiso {

    @Id
    @Column(name = "ID_ROL")
    private Long rolId;

    @Id
    @Column(name = "ID_PERMISO")
    private Long permisoId;
}
