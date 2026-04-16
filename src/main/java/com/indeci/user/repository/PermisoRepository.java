package com.indeci.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {
}