package com.indice.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indice.user.entity.UsuarioRol;
import com.indice.user.entity.UsuarioRolId;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

    List<UsuarioRol> findByUserId(Long userId);
}