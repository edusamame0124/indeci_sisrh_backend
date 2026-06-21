package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.dto.subsidio.SubsidioValidacionDto;
import com.indeci.rrhh.entity.SubsidioTramo;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.repository.SubsidioCittRepository;
import com.indeci.rrhh.repository.SubsidioLiquidacionRepository;
import com.indeci.rrhh.repository.SubsidioTramoRepository;
import com.indeci.rrhh.repository.SubsidioValidacionRegistroRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;

import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class SubsidioValidacionServiceTest {

    @Mock private SubsidioValidacionRegistroRepository validacionRepository;
    @Mock private SubsidioTramoRepository tramoRepository;
    @Mock private SubsidioLiquidacionRepository liquidacionRepository;
    @Mock private SubsidioCasoRepository casoRepository;
    @Mock private SubsidioCittRepository cittRepository;
    @Mock private PeriodoPlanillaRepository periodoRepository;

    @InjectMocks
    private SubsidioValidacionService service;

    @Test
    void detecta_tramos_solapados_SUB_V001() {
        SubsidioTramo a = tramo(1L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15));
        SubsidioTramo b = tramo(2L, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));

        List<SubsidioValidacionDto> result = service.validarTramosSolapados(5L, List.of(a, b));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).codigo()).isEqualTo("SUB_V001");
        assertThat(result.get(0).severidad()).isEqualTo(SubsidioEstados.SEVERIDAD_BLOQUEO);
    }

    private static SubsidioTramo tramo(Long id, LocalDate desde, LocalDate hasta) {
        SubsidioTramo t = new SubsidioTramo();
        t.setId(id);
        t.setFechaDesde(desde);
        t.setFechaHasta(hasta);
        return t;
    }
}
