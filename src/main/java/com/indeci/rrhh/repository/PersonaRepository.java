package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.Persona;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    boolean existsByDni(String dni);

    boolean existsByEmail(String email);

    Optional<Persona> findByDni(String dni);

    Optional<Persona> findByUserId(Long userId);

    /**
     * Proyección ligera para el listado: 1 JOIN entre INDECI_PERSONA e INDECI_EMPLEADO
     * + 1 subquery escalar para el régimen laboral vigente.
     * Elimina el N+1 del método listar() anterior (~5 900 queries → 1 query).
     * Columnas: [0]=ID [1]=EMPLEADO_ID [2]=NOMBRE_COMPLETO [3]=DNI [4]=CODIGO_INTERNO [5]=ESTADO [6]=REGIMEN_LABORAL
     */
    @Query(value = """
            SELECT
                p.ID,
                e.ID              AS EMPLEADO_ID,
                p.NOMBRE_COMPLETO,
                p.DNI,
                e.CODIGO_INTERNO,
                e.ESTADO,
                (SELECT rl.CODIGO
                   FROM GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ep
                   JOIN GESTIONRRHH.INDECI_REGIMEN_LABORAL   rl ON rl.ID = ep.REGIMEN_LABORAL_ID
                  WHERE ep.EMPLEADO_ID = e.ID
                    AND ep.ACTIVO = 1
                    AND ROWNUM = 1) AS REGIMEN_LABORAL
            FROM GESTIONRRHH.INDECI_PERSONA  p
            LEFT JOIN GESTIONRRHH.INDECI_EMPLEADO e ON e.PERSONA_ID = p.ID
            ORDER BY p.NOMBRE_COMPLETO ASC
            """, nativeQuery = true)
    List<Object[]> findAllResumenRaw();

    /**
     * Página de personas filtradas por nombre o DNI.
     * Parámetro :q debe llegar como patrón LIKE ya en mayúsculas (ej. "%TEXTO%"; "%" para todos).
     * Columnas: [0]=ID [1]=EMPLEADO_ID [2]=NOMBRE_COMPLETO [3]=DNI [4]=CODIGO_INTERNO [5]=ESTADO [6]=REGIMEN_LABORAL
     */
    @Query(value = """
            SELECT
                p.ID,
                e.ID              AS EMPLEADO_ID,
                p.NOMBRE_COMPLETO,
                p.DNI,
                e.CODIGO_INTERNO,
                e.ESTADO,
                (SELECT rl.CODIGO
                   FROM GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ep
                   JOIN GESTIONRRHH.INDECI_REGIMEN_LABORAL   rl ON rl.ID = ep.REGIMEN_LABORAL_ID
                  WHERE ep.EMPLEADO_ID = e.ID
                    AND ep.ACTIVO = 1
                    AND ROWNUM = 1) AS REGIMEN_LABORAL
            FROM GESTIONRRHH.INDECI_PERSONA  p
            LEFT JOIN GESTIONRRHH.INDECI_EMPLEADO e ON e.PERSONA_ID = p.ID
            WHERE (UPPER(p.NOMBRE_COMPLETO) LIKE :q OR p.DNI LIKE :q)
            ORDER BY p.NOMBRE_COMPLETO ASC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """, nativeQuery = true)
    List<Object[]> findPageResumenRaw(
            @Param("q") String q,
            @Param("offset") long offset,
            @Param("size") int size);

    /** Total de filas para el mismo filtro — necesario para calcular totalPages. */
    @Query(value = """
            SELECT COUNT(*)
            FROM GESTIONRRHH.INDECI_PERSONA  p
            LEFT JOIN GESTIONRRHH.INDECI_EMPLEADO e ON e.PERSONA_ID = p.ID
            WHERE (UPPER(p.NOMBRE_COMPLETO) LIKE :q OR p.DNI LIKE :q)
            """, nativeQuery = true)
    long countResumen(@Param("q") String q);
}