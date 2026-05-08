package com.indeci.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.UsuarioPermisoDeny;
import com.indeci.user.entity.UsuarioPermisoDenyId;

public interface UsuarioPermisoDenyRepository extends JpaRepository<UsuarioPermisoDeny, UsuarioPermisoDenyId> {

    List<UsuarioPermisoDeny> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
