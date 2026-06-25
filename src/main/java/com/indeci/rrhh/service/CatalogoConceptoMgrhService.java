package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.CatalogoConceptoMgrhDto;
import com.indeci.rrhh.entity.CatalogoConceptoMgrh;
import com.indeci.rrhh.repository.CatalogoConceptoMgrhRepository;

import jakarta.persistence.criteria.Predicate;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SPEC_HOMOLOGACION_MGRH §E (D4) — búsqueda paginada y reutilizable del catálogo
 * MGRH/MEF.
 *
 * <p>Filtra por: {@code codigo} (LIKE), {@code tipo} (=), {@code descripcionNorma}
 * (LIKE), {@code detalleNorma} (LIKE), {@code estado} (=), {@code soloSeleccionables}
 * ({@code SELECCIONABLE='S'}, excluye GASTOS POR ENCARGO) y {@code soloVigentes}
 * ({@code VIGENTE='S'}, versión anual más reciente). Los LIKE son case-insensitive y
 * parciales. La búsqueda se arma con {@link Specification} (patrón previsional, igual
 * que el historial de auditoría).</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogoConceptoMgrhService {

    private static final String SI = "S";

    private final CatalogoConceptoMgrhRepository repository;

    /** Criterios de búsqueda del catálogo MGRH (reutilizable). */
    @Value
    @Builder
    public static class Filtros {
        /**
         * Type-ahead: OR (case-insensitive, parcial) sobre DESCRIPCION_TIPO_CONCEPTO,
         * CODIGO_CONCEPTO_MGRH, DESCRIPCION_NORMA y DETALLE_NORMA. Un solo texto que
         * el usuario digita en el autocomplete de la pestaña Homologación.
         */
        String texto;
        /**
         * Tipo local del concepto (INGRESO|DESCUENTO|APORTE) → filtra el catálogo al
         * TIPO MGRH compatible (INGRESOS|EGRESOS|APORTES). Garantiza que solo se
         * ofrezcan conceptos del tipo permitido.
         */
        String tipoLocal;
        String codigo;
        String tipo;
        String descripcion;
        String detalle;
        String estado;
        /** {@code true} → solo ESTADO oficial 'Activo' (autocomplete usa esto). */
        boolean soloActivos;
        /** {@code true} (default API) → solo {@code SELECCIONABLE='S'} (excluye GASTOS POR ENCARGO). */
        boolean soloSeleccionables;
        /** {@code true} (default API) → solo {@code VIGENTE='S'} (versión anual más reciente). */
        boolean soloVigentes;
    }

    /** Mapea el tipo local del concepto al TIPO oficial del catálogo MGRH. */
    private static String tipoLocalAMgrh(String tipoLocal) {
        if (tipoLocal == null) {
            return null;
        }
        return switch (tipoLocal.trim().toUpperCase()) {
            case "INGRESO", "INGRESOS" -> "INGRESOS";
            case "DESCUENTO", "EGRESO", "EGRESOS" -> "EGRESOS";
            case "APORTE", "APORTES" -> "APORTES";
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public Page<CatalogoConceptoMgrhDto> buscar(Filtros filtros, Pageable pageable) {
        return repository.findAll(toSpecification(filtros), pageable).map(this::toDto);
    }

    private Specification<CatalogoConceptoMgrh> toSpecification(Filtros f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Type-ahead: un solo texto matchea Tipo Concepto, Código, Descripción o Detalle.
            if (!isBlank(f.getTexto())) {
                String like = "%" + f.getTexto().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("descripcionTipoConcepto")), like),
                        cb.like(cb.lower(root.get("codigoConceptoMgrh")), like),
                        cb.like(cb.lower(root.get("descripcionNorma")), like),
                        cb.like(cb.lower(root.get("detalleNorma")), like)));
            }

            // Compatibilidad: tipo local → TIPO MGRH permitido.
            String tipoMgrh = tipoLocalAMgrh(f.getTipoLocal());
            if (tipoMgrh != null) {
                predicates.add(cb.equal(root.get("tipo"), tipoMgrh));
            }
            if (f.isSoloActivos()) {
                predicates.add(cb.equal(cb.lower(root.get("estado")), "activo"));
            }

            addLike(predicates, cb, root.get("codigoConceptoMgrh"), f.getCodigo());
            addEquals(predicates, cb, root.get("tipo"), f.getTipo());
            addLike(predicates, cb, root.get("descripcionNorma"), f.getDescripcion());
            addLike(predicates, cb, root.get("detalleNorma"), f.getDetalle());
            addEquals(predicates, cb, root.get("estado"), f.getEstado());

            if (f.isSoloSeleccionables()) {
                predicates.add(cb.equal(root.get("seleccionable"), SI));
            }
            if (f.isSoloVigentes()) {
                predicates.add(cb.equal(root.get("vigente"), SI));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addLike(List<Predicate> predicates,
                         jakarta.persistence.criteria.CriteriaBuilder cb,
                         jakarta.persistence.criteria.Expression<String> column,
                         String value) {
        if (isBlank(value)) {
            return;
        }
        predicates.add(cb.like(cb.lower(column), "%" + value.trim().toLowerCase() + "%"));
    }

    private void addEquals(List<Predicate> predicates,
                           jakarta.persistence.criteria.CriteriaBuilder cb,
                           jakarta.persistence.criteria.Expression<String> column,
                           String value) {
        if (isBlank(value)) {
            return;
        }
        predicates.add(cb.equal(column, value.trim()));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private CatalogoConceptoMgrhDto toDto(CatalogoConceptoMgrh e) {
        CatalogoConceptoMgrhDto dto = new CatalogoConceptoMgrhDto();
        dto.setId(e.getId());
        dto.setTipo(e.getTipo());
        dto.setCodigoConceptoMgrh(e.getCodigoConceptoMgrh());
        dto.setDescripcionNorma(e.getDescripcionNorma());
        dto.setDetalleNorma(e.getDetalleNorma());
        dto.setFechaVigenciaTexto(e.getFechaVigenciaTexto());
        dto.setFechaVigenciaDate(e.getFechaVigenciaDate());
        dto.setImponible(e.getImponible());
        dto.setDescripcionTipoConcepto(e.getDescripcionTipoConcepto());
        dto.setTipoNorma(e.getTipoNorma());
        dto.setEstado(e.getEstado());
        dto.setSeleccionable(e.getSeleccionable());
        dto.setAnioCatalogo(e.getAnioCatalogo());
        dto.setVigente(e.getVigente());
        dto.setFuenteCatalogo(e.getFuenteCatalogo());
        return dto;
    }
}
