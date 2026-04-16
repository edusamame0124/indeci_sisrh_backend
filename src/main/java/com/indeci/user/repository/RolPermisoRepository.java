package com.indeci.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.indeci.user.entity.RolPermiso;
import com.indeci.user.entity.RolPermisoId;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermisoId> {

    List<RolPermiso> findByRolId(Long rolId);
}