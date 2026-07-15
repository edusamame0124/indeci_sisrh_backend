package com.indeci.sistema.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.indeci.sistema.entity.UsuarioSistema;

public interface UsuarioSistemaRepository extends JpaRepository<UsuarioSistema, Long> {

    /** Asignaciones activas de un usuario hacia cualquier sistema. */
    List<UsuarioSistema> findByUserIdAndActivo(Long userId, Integer activo);

    /** Asignaciones activas hacia un sistema (p. ej. 'rendimiento') — directorio GDR. */
    List<UsuarioSistema> findBySistemaIdAndActivo(Long sistemaId, Integer activo);

    List<UsuarioSistema> findByUserId(Long userId);

    Optional<UsuarioSistema> findByUserIdAndSistemaId(Long userId, Long sistemaId);

    /**
     * Directorio GDR: usuarios del sistema 'rendimiento' con el rol externo dado
     * (p. ej. GDR_USUARIO), filtrados por DNI (prefijo), nombre o username. Una
     * sola consulta (sin N+1). La persona se resuelve por USER_ID y, si falta el
     * enlace y el username es un DNI de 8 dígitos, por DNI = username.
     * Columnas: [0]=DNI [1]=NOMBRE [2]=USERNAME [3]=AREA_CODIGO [4]=AREA_NOMBRE [5]=ESTADO.
     */
    @Query(value = """
            SELECT COALESCE(p.DNI, pu.DNI)                         AS DNI,
                   COALESCE(p.NOMBRE_COMPLETO, pu.NOMBRE_COMPLETO) AS NOMBRE,
                   u.USERNAME                                      AS USERNAME,
                   us.AREA_CODIGO                                  AS AREA_CODIGO,
                   sa.NOMBRE_AREA                                  AS AREA_NOMBRE,
                   u.STATUS                                        AS ESTADO
              FROM GESTIONRRHH.INDECI_USUARIO_SISTEMA us
              JOIN GESTIONRRHH.INDECI_SISTEMA s
                ON s.ID = us.SISTEMA_ID AND s.CODIGO = 'rendimiento'
              JOIN GESTIONRRHH.USERS u
                ON u.ID = us.USER_ID AND UPPER(u.STATUS) = 'ACTIVE'
              LEFT JOIN GESTIONRRHH.INDECI_PERSONA p
                ON p.USER_ID = u.ID
              LEFT JOIN GESTIONRRHH.INDECI_PERSONA pu
                ON REGEXP_LIKE(u.USERNAME, '^[0-9]{8}$') AND pu.DNI = u.USERNAME
              LEFT JOIN GESTIONRRHH.INDECI_SISTEMA_AREA sa
                ON sa.SISTEMA_ID = us.SISTEMA_ID AND sa.CODIGO_AREA = us.AREA_CODIGO AND sa.ACTIVO = 1
             WHERE us.ACTIVO = 1
               AND UPPER(us.ROLES_EXTERNOS) LIKE '%' || :rol || '%'
               AND COALESCE(p.DNI, pu.DNI) IS NOT NULL
               AND ( COALESCE(p.DNI, pu.DNI) LIKE :qPrefix
                     OR UPPER(COALESCE(p.NOMBRE_COMPLETO, pu.NOMBRE_COMPLETO)) LIKE :qLike
                     OR UPPER(u.USERNAME) LIKE :qLike )
             ORDER BY NOMBRE
             FETCH FIRST 25 ROWS ONLY
            """, nativeQuery = true)
    List<Object[]> searchGdrDirectory(
            @Param("rol") String rol,
            @Param("qPrefix") String qPrefix,
            @Param("qLike") String qLike);
}
