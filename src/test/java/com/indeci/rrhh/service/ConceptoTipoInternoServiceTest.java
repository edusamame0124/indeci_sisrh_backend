package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.ConceptoTipoInternoDto;
import com.indeci.rrhh.entity.ConceptoTipoInterno;
import com.indeci.rrhh.repository.ConceptoTipoInternoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SPEC_CONCEPTOS_PLANILLA §13 — el catálogo "Tipo de Concepto" (SISPER) lista los
 * 8 valores ordenados, exponiendo la clasificación del motor derivada.
 */
@ExtendWith(MockitoExtension.class)
class ConceptoTipoInternoServiceTest {

    @Mock private ConceptoTipoInternoRepository repository;
    @InjectMocks private ConceptoTipoInternoService service;

    @Test
    void listar_devuelve_los_8_valores_ordenados_con_clasificacion_motor() {
        when(repository.findByActivoOrderByOrden(1)).thenReturn(List.of(
                fila("REM_FIJA",    "REMUNERACION FIJA",  "REMUNERATIVO",      1),
                fila("REINTEGRO",   "REINTEGRO",          "REMUNERATIVO",      2),
                fila("ENCARGATURA", "ENCARGATURA",        "REMUNERATIVO",      3),
                fila("INCENTIVOS",  "INCENTIVOS",         "NO_REMUNERATIVO",   4),
                fila("OTRA_REM",    "OTRA REMUNERACION",  "REMUNERATIVO",      5),
                fila("DESC_VAR",    "DESCUENTO VARIABLE", "DESCUENTO",         6),
                fila("DESC_FIJO",   "DESCUENTO FIJO",     "DESCUENTO",         7),
                fila("APORTE_TRAB", "APORTE TRABAJADOR",  "APORTE_TRABAJADOR", 8)));

        List<ConceptoTipoInternoDto> out = service.listar();

        assertThat(out).hasSize(8);
        assertThat(out).extracting(ConceptoTipoInternoDto::getCodigo)
                .containsExactly("REM_FIJA", "REINTEGRO", "ENCARGATURA", "INCENTIVOS",
                        "OTRA_REM", "DESC_VAR", "DESC_FIJO", "APORTE_TRAB");
        assertThat(out).extracting(ConceptoTipoInternoDto::getOrden)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

        ConceptoTipoInternoDto incentivos = out.get(3);
        assertThat(incentivos.getClasificacionMotor()).isEqualTo("NO_REMUNERATIVO");
    }

    private ConceptoTipoInterno fila(String codigo, String nombre,
                                     String clasificacionMotor, int orden) {
        ConceptoTipoInterno t = new ConceptoTipoInterno();
        t.setCodigo(codigo);
        t.setNombre(nombre);
        t.setClasificacionMotor(clasificacionMotor);
        t.setOrden(orden);
        t.setActivo(1);
        return t;
    }
}
