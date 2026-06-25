package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.CatalogoConceptoMgrhDto;
import com.indeci.rrhh.entity.CatalogoConceptoMgrh;
import com.indeci.rrhh.repository.CatalogoConceptoMgrhRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC_HOMOLOGACION_MGRH §H — búsqueda paginada del catálogo MGRH/MEF.
 *
 * <p>Verifica la traducción de filtros a predicados (LIKE case-insensitive por
 * código/descripción/detalle, igualdad por tipo/estado, {@code SELECCIONABLE='S'}
 * y {@code VIGENTE='S'}) capturando la {@link Specification} construida, y el mapeo
 * entidad → DTO con la página resultante.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogoConceptoMgrhServiceTest {

    @Mock private CatalogoConceptoMgrhRepository repository;

    @InjectMocks private CatalogoConceptoMgrhService service;

    // --- Mocks del Criteria API para inspeccionar los predicados generados ---
    @SuppressWarnings("unchecked")
    private final Root<CatalogoConceptoMgrh> root = mock(Root.class);
    @SuppressWarnings("unchecked")
    private final CriteriaQuery<Object> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Predicate andResult = mock(Predicate.class);

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Cada path/expr/predicate del Criteria devuelve un mock funcional.
        lenient().when(root.<String>get(any(String.class)))
                .thenAnswer(inv -> mock(Path.class));
        lenient().when(cb.lower(any())).thenAnswer(inv -> mock(Expression.class));
        lenient().when(cb.like(any(), any(String.class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.equal(any(), any(Object.class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(andResult);
    }

    /** Captura la Specification que la service pasa al repositorio y la "ejecuta". */
    @SuppressWarnings("unchecked")
    private Predicate[] capturarPredicados(CatalogoConceptoMgrhService.Filtros filtros) {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.buscar(filtros, pageable);

        ArgumentCaptor<Specification<CatalogoConceptoMgrh>> specCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));

        // Disparar la construcción de predicados sobre los mocks del Criteria.
        specCaptor.getValue().toPredicate(root, (CriteriaQuery<?>) query, cb);

        ArgumentCaptor<Predicate[]> andCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(andCaptor.capture());
        return andCaptor.getValue();
    }

    // ---- defaults (soloSeleccionables + soloVigentes) → 2 predicados de igualdad ----
    @Test
    void filtros_por_defecto_aplican_seleccionable_y_vigente() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .soloSeleccionables(true)
                .soloVigentes(true)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        // 2 equals: SELECCIONABLE='S' y VIGENTE='S' (ambos con valor "S").
        assertThat(predicados).hasSize(2);
        verify(cb, times(2)).equal(any(), org.mockito.ArgumentMatchers.eq("S"));
    }

    // ---- soloSeleccionables=false → NO se agrega el predicado SELECCIONABLE='S' ----
    @Test
    void soloSeleccionables_false_no_filtra_por_seleccionable() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        // Sin filtros de texto ni flags → ningún predicado (incluye GASTOS POR ENCARGO).
        assertThat(predicados).isEmpty();
    }

    // ---- búsqueda por codigo/tipo/descripcion/detalle/estado → LIKE lower + equals ----
    @Test
    void filtros_de_texto_generan_like_case_insensitive_y_equals() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .codigo("0001")
                .tipo("INGRESOS")
                .descripcion("bonif")
                .detalle("escala")
                .estado("Activo")
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        // 3 LIKE (codigo, descripcion, detalle) + 2 equals (tipo, estado).
        assertThat(predicados).hasSize(5);
        // LIKE en minúsculas y parcial (case-insensitive).
        verify(cb).like(any(), org.mockito.ArgumentMatchers.eq("%0001%"));
        verify(cb).like(any(), org.mockito.ArgumentMatchers.eq("%bonif%"));
        verify(cb).like(any(), org.mockito.ArgumentMatchers.eq("%escala%"));
        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq("INGRESOS"));
        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq("Activo"));
    }

    // ---- texto type-ahead → un solo OR sobre tipoConcepto/codigo/descripcion/detalle ----
    @Test
    void texto_genera_un_or_sobre_tipoConcepto_codigo_descripcion_y_detalle() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .texto("Remun")
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        // Un único predicado: el OR agrupado.
        assertThat(predicados).hasSize(1);
        verify(cb).or(any(Predicate[].class));
        // 4 LIKE con el mismo patrón parcial en minúsculas (tipoConcepto/codigo/descripcion/detalle).
        verify(cb, times(4)).like(any(), org.mockito.ArgumentMatchers.eq("%remun%"));
    }

    // ---- tipoLocal mapea al TIPO MGRH compatible (INGRESO→INGRESOS, DESCUENTO→EGRESOS) ----
    @Test
    void tipoLocal_ingreso_filtra_tipo_mgrh_ingresos() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .tipoLocal("INGRESO")
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        assertThat(predicados).hasSize(1);
        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq("INGRESOS"));
    }

    @Test
    void tipoLocal_descuento_filtra_tipo_mgrh_egresos() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .tipoLocal("DESCUENTO")
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        assertThat(predicados).hasSize(1);
        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq("EGRESOS"));
    }

    // ---- soloActivos → ESTADO='Activo' (case-insensitive) ----
    @Test
    void soloActivos_filtra_estado_activo() {
        CatalogoConceptoMgrhService.Filtros f = CatalogoConceptoMgrhService.Filtros.builder()
                .soloActivos(true)
                .soloSeleccionables(false)
                .soloVigentes(false)
                .build();

        Predicate[] predicados = capturarPredicados(f);

        assertThat(predicados).hasSize(1);
        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq("activo"));
    }

    // ---- mapeo entidad → DTO en la página resultante ----
    @Test
    @SuppressWarnings("unchecked")
    void buscar_mapea_entidad_a_dto() {
        CatalogoConceptoMgrh e = new CatalogoConceptoMgrh();
        e.setId(1L);
        e.setTipo("INGRESOS");
        e.setCodigoConceptoMgrh("0001");
        e.setDescripcionNorma("DS.051-91 Bonif.Dif.");
        e.setSeleccionable("S");
        e.setVigente("S");
        e.setAnioCatalogo(2026);
        e.setFuenteCatalogo("Conceptos2026.xls");

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));

        Page<CatalogoConceptoMgrhDto> page = service.buscar(
                CatalogoConceptoMgrhService.Filtros.builder().build(), pageable);

        assertThat(page.getContent()).hasSize(1);
        CatalogoConceptoMgrhDto dto = page.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getCodigoConceptoMgrh()).isEqualTo("0001");
        assertThat(dto.getTipo()).isEqualTo("INGRESOS");
        assertThat(dto.getAnioCatalogo()).isEqualTo(2026);
        assertThat(dto.getFuenteCatalogo()).isEqualTo("Conceptos2026.xls");
    }
}
