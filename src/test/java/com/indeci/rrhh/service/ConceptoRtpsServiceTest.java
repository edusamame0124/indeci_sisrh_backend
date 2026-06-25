package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.ConceptoRtpsDto;
import com.indeci.rrhh.entity.ConceptoRtps;
import com.indeci.rrhh.repository.ConceptoRtpsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 (§10.4.1) — el catálogo RTPS marca los grupos
 * ({@code ES_GRUPO='S'}) como NO seleccionables y preserva los ceros del código.
 */
@ExtendWith(MockitoExtension.class)
class ConceptoRtpsServiceTest {

    @Mock private ConceptoRtpsRepository repository;
    @InjectMocks private ConceptoRtpsService service;

    @Test
    void listar_grupos_no_son_seleccionables_e_items_si() {
        when(repository.findByActivoOrderByOrden(1)).thenReturn(List.of(
                fila("0700", "DESCUENTOS", "S", 700),
                fila("0703", "DESC. AUTORIZADO", "N", 703),
                fila("0704", "ADELANTOS Y PRESTAMOS", "N", 704)));

        List<ConceptoRtpsDto> out = service.listar();

        ConceptoRtpsDto grupo = porCodigo(out, "0700");
        ConceptoRtpsDto item0703 = porCodigo(out, "0703");
        ConceptoRtpsDto item0704 = porCodigo(out, "0704");

        assertThat(grupo.isSeleccionable()).isFalse();   // cabecera de grupo
        assertThat(item0703.isSeleccionable()).isTrue();
        assertThat(item0704.isSeleccionable()).isTrue();

        // Preserva ceros a la izquierda.
        assertThat(item0703.getCodigo()).isEqualTo("0703");
        assertThat(item0704.getCodigo()).isEqualTo("0704");

        // Ningún grupo aparece como seleccionable.
        assertThat(out.stream().filter(ConceptoRtpsDto::isSeleccionable)
                .map(ConceptoRtpsDto::getCodigo)).containsExactly("0703", "0704");
    }

    private ConceptoRtps fila(String codigo, String desc, String esGrupo, int orden) {
        ConceptoRtps r = new ConceptoRtps();
        r.setCodigo(codigo);
        r.setDescripcion(desc);
        r.setEsGrupo(esGrupo);
        r.setOrden(orden);
        r.setActivo(1);
        return r;
    }

    private ConceptoRtpsDto porCodigo(List<ConceptoRtpsDto> list, String codigo) {
        return list.stream().filter(d -> codigo.equals(d.getCodigo()))
                .findFirst().orElseThrow();
    }
}
