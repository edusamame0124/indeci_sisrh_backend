package com.indice.user.entity;

import java.io.Serializable;
import java.util.Objects;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPermisoDenyId implements Serializable {

    private Long userId;
    private Long permisoId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsuarioPermisoDenyId that)) return false;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(permisoId, that.permisoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, permisoId);
    }
}