package com.indeci.rrhh.repository;

import com.indeci.rrhh.entity.CatalogoConceptoMgrh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SPEC_HOMOLOGACION_MGRH §E — repositorio del catálogo MGRH/MEF.
 *
 * <p>La búsqueda paginada se arma con {@link JpaSpecificationExecutor} (mismo patrón
 * que {@code AuditoriaRepository}); los predicados (LIKE case-insensitive por código /
 * descripción / detalle, igualdad por tipo / estado, filtros {@code SELECCIONABLE='S'}
 * y {@code VIGENTE='S'}) se construyen en
 * {@code CatalogoConceptoMgrhService}.</p>
 */
public interface CatalogoConceptoMgrhRepository
        extends JpaRepository<CatalogoConceptoMgrh, Long>,
                JpaSpecificationExecutor<CatalogoConceptoMgrh> {
}
