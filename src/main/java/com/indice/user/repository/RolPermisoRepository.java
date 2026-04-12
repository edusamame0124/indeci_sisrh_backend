package com.indice.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indice.user.entity.RolPermiso;
import com.indice.user.entity.RolPermisoId;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermisoId> {

    List<RolPermiso> findByRolId(Long rolId);
}