package com.indice.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indice.user.entity.Rol;

public interface RolRepository extends JpaRepository<Rol, Long> {
}