package com.indice.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indice.user.entity.Permiso;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {
}