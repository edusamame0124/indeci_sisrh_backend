package com.indeci.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.Rol;

public interface RolRepository extends JpaRepository<Rol, Long> {
}