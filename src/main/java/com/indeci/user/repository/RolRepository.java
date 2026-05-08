package com.indeci.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.indeci.user.entity.Rol;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findFirstByCodigoIgnoreCase(String codigo);

    /**
     * Roles considerados vigentes para asignación automática por alta administrativa (sin properties).
     * Orden: menor nivel (sin nivel al final), luego menor ID estable.
     */
    @Query(
            """
            select r from Rol r
             where upper(trim(coalesce(r.activo,''))) in ('S', 'SI', '1', 'Y', 'YES', 'ACTIVO')
             order by case when r.nivel is null then 1 else 0 end, r.nivel asc, r.id asc""")
    List<Rol> findEligibleRolesForAutoAssign(Pageable pageable);
}