package com.indeci.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.UsuarioRol;
import com.indeci.user.entity.UsuarioRolId;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

    List<UsuarioRol> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}