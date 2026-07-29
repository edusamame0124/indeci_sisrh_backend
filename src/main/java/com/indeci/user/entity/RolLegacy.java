package com.indeci.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Catálogo legacy independiente de SS_ROL (4 filas: Administrador/Empleado/
 * Branch Admin/Admin Licencias), usado únicamente como padre de la FK
 * histórica USERS.ROLE_ID (FK_USERS_USERS_IBFK_1). Sin relación con el
 * catálogo de roles Fase 1 (SS_ROL/SS_ROL_PERMISO/SS_USUARIO_ROL) — no
 * confundir con {@link Rol}.
 */
@Entity
@Table(name = "ROLES", schema = "GESTIONRRHH")
@Data
public class RolLegacy {

    @Id
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME")
    private String name;
}
