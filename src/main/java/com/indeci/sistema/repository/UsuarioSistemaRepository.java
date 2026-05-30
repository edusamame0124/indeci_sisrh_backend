package com.indeci.sistema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.sistema.entity.UsuarioSistema;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Long> {

    /** Asignaciones activas de un usuario hacia cualquier sistema. */
    List<UsuarioSistema> findByUserIdAndActivo(Long userId, Integer activo);
}
