package com.indeci.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.UsuarioPermiso;
import com.indeci.user.entity.UsuarioPermisoId;

public interface UsuarioPermisoRepository extends JpaRepository<UsuarioPermiso, UsuarioPermisoId> {

    List<UsuarioPermiso> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
